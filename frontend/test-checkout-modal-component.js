import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import axios from 'axios';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', {
  url: 'http://localhost/',
});
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.Node = dom.window.Node;
globalThis.MutationObserver = dom.window.MutationObserver;
globalThis.getComputedStyle = dom.window.getComputedStyle;
Object.defineProperty(globalThis, 'navigator', {
  configurable: true,
  value: dom.window.navigator,
});
Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', {
  configurable: true,
  value: true,
  writable: true,
});

const { cleanup, fireEvent, render, screen, waitFor } = await import('@testing-library/react');
const originalPost = axios.post;
const originalGet = axios.get;
let postHandler;
let getHandler;
axios.post = (...args) => postHandler(...args);
axios.get = (...args) => getHandler(...args);

let currentAuthUser = null;
globalThis.__mockAuthModule = {
  useAuth: () => ({
    user: currentAuthUser,
    token: currentAuthUser ? 'test-token' : null,
    isAuthenticated: Boolean(currentAuthUser),
    isLoading: false,
    logout: () => { currentAuthUser = null; },
    setSessionUser: (user) => { currentAuthUser = user; },
  }),
};

const componentPath = path.resolve(import.meta.dirname, 'src/components/marketplace/CheckoutModal.tsx');
const compiledPath = path.resolve(import.meta.dirname, '.test-compiled-CheckoutModal.mjs');
const compiled = esbuild.buildSync({
  entryPoints: [componentPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios', 'lucide-react', 'react-router-dom', '../../context/AuthContext'],
});
const source = compiled.outputFiles[0].text.replace(
  /import\s*\{[^}]*useAuth[^}]*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
  'const { useAuth } = globalThis.__mockAuthModule;',
);
fs.writeFileSync(compiledPath, source, 'utf8');
const { CheckoutModal } = await import(pathToFileURL(compiledPath).href);

const playbook = {
  id: 1,
  programId: 101,
  title: 'FinOps y Transformación Digital Cloud',
  tier: 'ENTERPRISE',
  price: 799,
  currency: 'USD',
  active: true,
};
const account = {
  id: 'bcp-oficial',
  banco: 'BCP',
  titular: 'LYSANDRI S.A.C.',
  moneda: 'USD',
  tipoCuenta: 'CORRIENTE',
  numeroCuenta: '193-98218491-0-28',
  cci: '002-193-009821849102-18',
  activo: true,
  estado: 'OFICIAL_ACTIVA',
  instrucciones: 'Cuenta para transferencia',
};
const createUrl = '/api/v1/orders/create-checkout-session';
const guestUrl = '/api/v1/orders/guest-session';
let assertions = 0;

function resetStorage() {
  window.sessionStorage.clear();
  currentAuthUser = null;
  window.open = () => {};
}

function renderModal() {
  return render(React.createElement(CheckoutModal, {
    isOpen: true,
    onClose: () => {},
    playbook,
  }));
}

async function selectTransferAndFill(email = 'carlos@empresa.pe') {
  fireEvent.click(screen.getByText('Transferencia Bancaria', { exact: true }));
  fireEvent.change(screen.getByPlaceholderText('Ej. Carlos Alberto Mendoza Alarcón'), {
    target: { value: 'Carlos Mendoza' },
  });
  fireEvent.change(screen.getByPlaceholderText('c.mendoza@empresa.com'), {
    target: { value: email },
  });
  fireEvent.change(screen.getByPlaceholderText('Ej. 45892147'), {
    target: { value: '70809010' },
  });
  await waitFor(() => assert.ok(screen.getByText('193-98218491-0-28')));
}

function createRequestCall(calls, handler) {
  return async (url, data, config) => {
    if (url.includes(guestUrl)) {
      calls.guest.push({ url, data, config });
      return { data: { guestSessionToken: 'guest-token-test-101' } };
    }
    if (url.includes(createUrl)) {
      calls.create.push({ url, data, config });
      return handler(url, data, config);
    }
    throw new Error(`POST inesperado: ${url}`);
  };
}

function successfulAccountRequest() {
  getHandler = async (url) => {
    if (url.includes('cuentas-bancarias')) return { data: [account] };
    throw new Error(`GET inesperado: ${url}`);
  };
}

try {
  console.log('PRUEBAS DE INTERACCIÓN REAL: CheckoutModal con React Testing Library + jsdom');

  // a. Compra invitada: el estado de intención se guarda antes de emitir la creación.
  resetStorage();
  let calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = createRequestCall(calls, async (_url, data, config) => {
    const saved = [...Array(window.sessionStorage.length)]
      .map((_, index) => window.sessionStorage.key(index))
      .filter((key) => key?.startsWith('lysandri_checkout_intent_guest_carlos@empresa.pe_'))
      .map((key) => JSON.parse(window.sessionStorage.getItem(key)));
    assert.equal(saved.length, 1, 'La intención debe estar guardada antes de la llamada HTTP');
    assert.equal(saved[0].guestSessionToken, 'guest-token-test-101');
    assert.equal(saved[0].claveIdempotencia, config.headers['X-Idempotency-Key']);
    assert.equal(data.guestSessionToken, 'guest-token-test-101');
    return { data: { codigoOrden: 'ORD-UI-001', guestSessionToken: 'guest-token-test-101' } };
  });
  renderModal();
  await selectTransferAndFill();
  fireEvent.click(screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i }));
  await waitFor(() => assert.ok(screen.getByText('ORD-UI-001')));
  assert.equal(calls.guest.length, 1);
  assert.equal(calls.create.length, 1);
  assert.ok(calls.create[0].config.headers['X-Idempotency-Key']);
  assert.equal(calls.create[0].config.headers['X-Guest-Session-Token'], 'guest-token-test-101');
  cleanup();
  assertions++;
  console.log('PASS UI a: credencial y clave persistidas antes del POST de creación.');

  // b. Fallo obteniendo guest-session: no crea orden y se recupera el botón.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = async (url) => {
    if (url.includes(guestUrl)) {
      calls.guest.push(url);
      throw new Error('No hay conexión con el servidor');
    }
    if (url.includes(createUrl)) calls.create.push(url);
    throw new Error(`POST inesperado: ${url}`);
  };
  renderModal();
  await selectTransferAndFill('fallo@test.pe');
  const submitButton = screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i });
  fireEvent.click(submitButton);
  await screen.findByText(/No hay conexión con el servidor/);
  await waitFor(() => assert.equal(submitButton.disabled, false));
  assert.equal(calls.guest.length, 1);
  assert.equal(calls.create.length, 0);
  cleanup();
  assertions++;
  console.log('PASS UI b: error de guest-session, botón recuperado y cero POST de creación.');

  // c. Timeout tras registrar: reintento desde UI con mismo idempotency key y credencial.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  const registeredByKey = new Map();
  postHandler = createRequestCall(calls, async (_url, data, config) => {
    const key = config.headers['X-Idempotency-Key'];
    const token = config.headers['X-Guest-Session-Token'];
    if (!registeredByKey.has(key)) registeredByKey.set(key, `ORD-RETRY-${registeredByKey.size + 1}`);
    if (calls.create.length === 1) {
      const error = new Error('timeout after server registered the order');
      error.code = 'ECONNABORTED';
      throw error;
    }
    assert.equal(data.guestSessionToken, token);
    return { data: { codigoOrden: registeredByKey.get(key), guestSessionToken: token } };
  });
  renderModal();
  await selectTransferAndFill('reintento@empresa.pe');
  const retryButton = screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i });
  fireEvent.click(retryButton);
  await screen.findByText(/Tiempo de espera agotado/);
  await waitFor(() => assert.equal(retryButton.disabled, false));
  fireEvent.click(retryButton);
  await waitFor(() => assert.ok(screen.getByText('ORD-RETRY-1')));
  assert.equal(calls.guest.length, 1, 'El segundo intento debe reutilizar la credencial conservada');
  assert.equal(calls.create.length, 2);
  assert.equal(calls.create[0].config.headers['X-Idempotency-Key'], calls.create[1].config.headers['X-Idempotency-Key']);
  assert.equal(calls.create[0].config.headers['X-Guest-Session-Token'], calls.create[1].config.headers['X-Guest-Session-Token']);
  assert.equal(registeredByKey.size, 1, 'El backend idempotente simulado registra una única orden');
  cleanup();
  assertions++;
  console.log('PASS UI c: retry tras timeout mantiene clave/token y no duplica el registro simulado.');

  // d. Desmontar/montar con el mismo sessionStorage recupera la orden sin otro POST.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = createRequestCall(calls, async () => ({
    data: { codigoOrden: 'ORD-RELOAD-1', guestSessionToken: 'guest-token-test-101' },
  }));
  const firstMount = renderModal();
  await selectTransferAndFill('recarga@empresa.pe');
  fireEvent.click(screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i }));
  await screen.findByText('ORD-RELOAD-1');
  const createdBeforeReload = calls.create.length;
  firstMount.unmount();
  renderModal();
  await screen.findByText('ORD-RELOAD-1');
  assert.equal(calls.create.length, createdBeforeReload);
  cleanup();
  assertions++;
  console.log('PASS UI d: remount recupera la orden persistida sin crear otra.');

  // e. Conflicto 409 se muestra, no anuncia éxito y libera el botón.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = createRequestCall(calls, async () => {
    const error = new Error('HTTP 409');
    error.response = { status: 409, data: { message: 'Conflicto de idempotencia para esta compra' } };
    throw error;
  });
  renderModal();
  await selectTransferAndFill('conflicto@test.pe');
  const conflictButton = screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i });
  fireEvent.click(conflictButton);
  await screen.findByText('Conflicto de idempotencia para esta compra');
  await waitFor(() => assert.equal(conflictButton.disabled, false));
  assert.equal(calls.create.length, 1);
  assert.equal(screen.queryByText(/¡Orden Registrada en el Sistema Lysandri!/), null);
  assert.equal(screen.queryByRole('button', { name: /Enviar Comprobante por WhatsApp/i }), null);
  cleanup();
  assertions++;
  console.log('PASS UI e: 409 visible, sin éxito ni WhatsApp, y botón reactivado.');

  // f. Al cambiar la identidad del comprador, no conserva el voucher anterior.
  resetStorage();
  window.sessionStorage.setItem('lysandri_checkout_intent_guest_primero@test.pe_101', JSON.stringify({
    claveIdempotencia: 'old-key',
    programId: 101,
    buyerScope: 'guest_primero@test.pe',
    email: 'primero@test.pe',
    nombreCompleto: 'Comprador Primero',
    tipoComprobante: 'BOLETA',
    numeroDocumento: '11111111',
    metodoPago: 'Transferencia Bancaria',
    codigoOrden: 'ORD-ANTERIOR',
    guestSessionToken: 'old-token',
    status: 'REGISTRADA',
    createdAt: Date.now(),
  }));
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = async (url) => { throw new Error(`POST inesperado: ${url}`); };
  const buyerMount = renderModal();
  await screen.findByText('ORD-ANTERIOR');
  currentAuthUser = { id: 202, email: 'segundo@test.pe', nombre: 'Comprador Segundo' };
  buyerMount.rerender(React.createElement(CheckoutModal, { isOpen: true, onClose: () => {}, playbook }));
  await waitFor(() => assert.equal(screen.queryByText('ORD-ANTERIOR'), null));
  assert.equal(calls.create.length, 0);
  cleanup();
  assertions++;
  console.log('PASS UI f: cambio de comprador no muestra ni recupera la orden previa.');

  // g. Solo la acción explícita de nueva orden cambia la clave.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = createRequestCall(calls, async () => ({
    data: { codigoOrden: 'ORD-OLD-INTENT', guestSessionToken: 'guest-token-test-101' },
  }));
  renderModal();
  await selectTransferAndFill('nueva@test.pe');
  fireEvent.click(screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i }));
  await screen.findByText('ORD-OLD-INTENT');
  const previousKey = calls.create[0].config.headers['X-Idempotency-Key'];
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar Nueva Orden' }));
  fireEvent.click(screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i }));
  await waitFor(() => assert.equal(calls.create.length, 2));
  assert.notEqual(calls.create[1].config.headers['X-Idempotency-Key'], previousKey);
  cleanup();
  assertions++;
  console.log('PASS UI g: nueva compra explícita genera otra clave.');

  // h. El destino backend aparece en pantalla y los errores de carga se muestran.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  postHandler = async (url) => { throw new Error(`POST inesperado: ${url}`); };
  renderModal();
  fireEvent.click(screen.getByText('Transferencia Bancaria', { exact: true }));
  await screen.findByText('193-98218491-0-28');
  assert.ok(screen.getByText('BCP'));
  cleanup();
  resetStorage();
  getHandler = async () => {
    const error = new Error('Backend cuentas no disponible');
    error.response = { data: { message: 'No se pudieron cargar destinos por mantenimiento' } };
    throw error;
  };
  renderModal();
  fireEvent.click(screen.getByText('Transferencia Bancaria', { exact: true }));
  await screen.findByText('No se pudieron cargar destinos por mantenimiento');
  cleanup();
  assertions++;
  console.log('PASS UI h: destino visible y error de carga backend mostrado.');

  // i. Error de red no muestra éxito, no abre WhatsApp y restaura el botón.
  resetStorage();
  calls = { guest: [], create: [] };
  successfulAccountRequest();
  window.openCalls = [];
  window.open = (...args) => window.openCalls.push(args);
  postHandler = createRequestCall(calls, async () => {
    throw new Error('No se pudo conectar con el servidor para registrar la orden.');
  });
  renderModal();
  await selectTransferAndFill('red@test.pe');
  const networkButton = screen.getByRole('button', { name: /Registrar Orden y Continuar con Voucher/i });
  fireEvent.click(networkButton);
  await screen.findByText(/No se pudo conectar con el servidor/);
  await waitFor(() => assert.equal(networkButton.disabled, false));
  assert.equal(calls.create.length, 1);
  assert.equal(window.openCalls.length, 0);
  assert.equal(screen.queryByText(/¡Orden Registrada en el Sistema Lysandri!/), null);
  cleanup();
  assertions++;
  console.log('PASS UI i: error de red no genera éxito ni abre WhatsApp.');

  console.log(`RESULTADO UI: ${assertions}/9 escenarios aprobados (interacciones DOM reales).`);
} finally {
  cleanup();
  axios.post = originalPost;
  axios.get = originalGet;
  dom.window.close();
  if (fs.existsSync(compiledPath)) fs.unlinkSync(compiledPath);
}
