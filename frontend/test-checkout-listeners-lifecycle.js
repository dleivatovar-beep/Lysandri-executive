import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import axios from 'axios';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><head></head><body></body></html>', {
  url: 'http://localhost/',
});
globalThis.window = dom.window;
globalThis.document = dom.window.document;

// Configurar window.location.href mutable para registrar redirecciones
let currentHref = 'http://localhost/';
try {
  Object.defineProperty(dom.window.location, 'href', {
    configurable: true,
    get() { return currentHref; },
    set(val) { currentHref = val; },
  });
} catch (e) {
  // fallback si no configurable
}
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: dom.window.localStorage,
});
Object.defineProperty(globalThis, 'sessionStorage', {
  configurable: true,
  value: dom.window.sessionStorage,
});
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

const { cleanup, fireEvent, render, screen, waitFor, act } = await import('@testing-library/react');
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
const compiledPath = path.resolve(import.meta.dirname, '.test-compiled-CheckoutModal-lifecycle.mjs');
const compiled = esbuild.buildSync({
  entryPoints: [componentPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios', 'lucide-react', 'react-router-dom', '../../context/AuthContext'],
  loader: { '.svg': 'text', '.png': 'dataurl', '.css': 'empty' },
});
const source = compiled.outputFiles[0].text
  .replace(
    /import\s*\{[^}]*useAuth[^}]*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
    'const { useAuth } = globalThis.__mockAuthModule;',
  )
  .replace(
    /window\.location\.href\s*=\s*([^;]+);/g,
    '((val) => { window.__mockLocationHref = val; })( $1 );',
  );
fs.writeFileSync(compiledPath, source, 'utf8');
const {
  CheckoutModal,
  resetKryptonScriptLoader,
  getKryptonScriptStatus,
  getGlobalSdkOwnership,
  resetGlobalSdkOwnership,
} = await import(pathToFileURL(compiledPath).href);

const createUrl = '/api/v1/orders/create-checkout-session';
const guestUrl = '/api/v1/orders/guest-session';

function handlePostRequest(handler) {
  return async (url, data, config) => {
    if (url.includes(guestUrl)) {
      return { data: { guestSessionToken: 'guest-token-lifecycle-test' } };
    }
    if (url.includes(createUrl)) {
      return handler(url, data, config);
    }
    throw new Error(`POST inesperado: ${url}`);
  };
}

const playbook = {
  id: 1,
  programId: 101,
  title: 'Programa Directivo de FinOps y Cloud Economics',
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
  instrucciones: 'Cuenta oficial para transferencias',
};

/**
 * SDK simulado de Krypton con asincronía realista (contrato oficial Lyra / MiCuentaWeb):
 * - KR.removeForms() devuelve una Promise real; soporta diferido y rechazo simulado.
 * - KR.setFormToken(token) devuelve una Promise real; soporta diferido y rechazo simulado.
 * - KR.renderElements(selector) devuelve una Promise real; soporta diferido, rechazo y "already rendered".
 *   Nota: setFormToken no simula que el formulario esté creado; la disponibilidad se confirma en renderElements().
 * - KR.removeEventCallbacks() devuelve una Promise real.
 * - Sin sleeps arbitrarios: resolución/rechazo determinísticos mediante métodos de control.
 */
class SimulatedKryptonSdk {
  constructor() {
    this.currentFormToken = null;
    this.errorCallbacks = [];
    this.submitCallbacks = [];
    this.removeFormsCalls = 0;
    this.removeCallbacksCalls = 0;
    this.setFormTokenCalls = [];
    this.renderElementsCalls = [];

    // Control diferido y rechazo para removeForms
    this.deferRemoveForms = false;
    this._removeFormsDeferreds = [];
    this.shouldRejectRemoveForms = false;
    this.removeFormsRejectError = new Error('Fallo simulado al eliminar formularios de pasarela izipay');

    // Control diferido y rechazo para setFormToken
    this.deferSetFormToken = false;
    this._setFormTokenDeferreds = [];
    this.shouldRejectSetFormToken = false;
    this.setFormTokenRejectError = new Error('Fallo simulado al configurar formToken en SDK');

    // Control diferido y rechazo para renderElements
    this.deferRenderElements = false;
    this._renderElementsDeferreds = [];
    this.shouldRejectRenderElements = false;
    this.renderElementsRejectError = new Error('Fallo simulado al renderizar elementos del formulario izipay');
    this.simulateAlreadyRendered = false;

    // Registro de operaciones
    this.operationsLog = [];
  }

  removeForms() {
    this.removeFormsCalls++;
    this.operationsLog.push({ op: 'removeForms', time: Date.now() });

    if (this.deferRemoveForms) {
      return new Promise((resolve, reject) => {
        this._removeFormsDeferreds.push({
          resolve: () => {
            if (this.shouldRejectRemoveForms) {
              reject(this.removeFormsRejectError);
            } else {
              this.currentFormToken = null;
              this.removeEventCallbacks();
              resolve();
            }
          },
          reject: (err) => {
            reject(err || this.removeFormsRejectError);
          },
        });
      });
    }

    if (this.shouldRejectRemoveForms) {
      return Promise.reject(this.removeFormsRejectError);
    }

    this.currentFormToken = null;
    this.removeEventCallbacks();
    return Promise.resolve();
  }

  resolveRemoveForms() {
    const pending = this._removeFormsDeferreds.shift();
    if (pending) pending.resolve();
  }

  rejectRemoveForms(err) {
    const pending = this._removeFormsDeferreds.shift();
    if (pending) pending.reject(err);
  }

  resolveAllRemoveForms() {
    while (this._removeFormsDeferreds.length > 0) {
      this.resolveRemoveForms();
    }
  }

  setFormToken(token) {
    this.setFormTokenCalls.push(token);
    this.operationsLog.push({ op: 'setFormToken', token, time: Date.now() });

    if (this.deferSetFormToken) {
      return new Promise((resolve, reject) => {
        this._setFormTokenDeferreds.push({
          resolve: () => {
            if (this.shouldRejectSetFormToken) {
              reject(this.setFormTokenRejectError);
            } else {
              this.currentFormToken = token;
              // El formulario no se crea aquí; se confirma en renderElements()
              resolve();
            }
          },
          reject: (err) => {
            reject(err || this.setFormTokenRejectError);
          },
        });
      });
    }

    if (this.shouldRejectSetFormToken) {
      return Promise.reject(this.setFormTokenRejectError);
    }

    this.currentFormToken = token;
    return Promise.resolve();
  }

  resolveSetFormToken() {
    const pending = this._setFormTokenDeferreds.shift();
    if (pending) pending.resolve();
  }

  rejectSetFormToken(err) {
    const pending = this._setFormTokenDeferreds.shift();
    if (pending) pending.reject(err);
  }

  resolveAllSetFormToken() {
    while (this._setFormTokenDeferreds.length > 0) {
      this.resolveSetFormToken();
    }
  }

  renderElements(selector = '.kr-embedded') {
    this.renderElementsCalls.push(selector);
    this.operationsLog.push({ op: 'renderElements', selector, time: Date.now() });

    if (this.deferRenderElements) {
      return new Promise((resolve, reject) => {
        this._renderElementsDeferreds.push({
          resolve: () => {
            if (this.shouldRejectRenderElements) {
              reject(this.renderElementsRejectError);
            } else if (this.simulateAlreadyRendered) {
              reject(new Error('Elements already rendered'));
            } else {
              resolve({ result: 'rendered', selector });
            }
          },
          reject: (err) => {
            reject(err || this.renderElementsRejectError);
          },
        });
      });
    }

    if (this.shouldRejectRenderElements) {
      return Promise.reject(this.renderElementsRejectError);
    }

    if (this.simulateAlreadyRendered) {
      return Promise.reject(new Error('Elements already rendered'));
    }

    return Promise.resolve({ result: 'rendered', selector });
  }

  resolveRenderElements() {
    const pending = this._renderElementsDeferreds.shift();
    if (pending) pending.resolve();
  }

  rejectRenderElements(err) {
    const pending = this._renderElementsDeferreds.shift();
    if (pending) pending.reject(err);
  }

  resolveAllRenderElements() {
    while (this._renderElementsDeferreds.length > 0) {
      this.resolveRenderElements();
    }
  }

  onError(cb) {
    this.errorCallbacks.push(cb);
    this.operationsLog.push({ op: 'onError', time: Date.now() });
  }

  onSubmit(cb) {
    this.submitCallbacks.push(cb);
    this.operationsLog.push({ op: 'onSubmit', time: Date.now() });
  }

  removeEventCallbacks() {
    this.removeCallbacksCalls++;
    this.errorCallbacks = [];
    this.submitCallbacks = [];
    this.operationsLog.push({ op: 'removeEventCallbacks', time: Date.now() });
    return Promise.resolve();
  }

  dispatchError(ev) {
    for (const cb of [...this.errorCallbacks]) {
      cb(ev);
    }
  }

  dispatchSubmit(data) {
    let result = false;
    for (const cb of [...this.submitCallbacks]) {
      const res = cb(data);
      if (res) result = true;
    }
    return result;
  }
}

let testsPassed = 0;

function resetEnv() {
  window.sessionStorage.clear();
  currentAuthUser = null;
  window.__krCoordinator = null;
  delete window.__mockLocationHref;
  resetKryptonScriptLoader();
  resetGlobalSdkOwnership();
}

function fillAndSubmitForm(name = 'Alberto Morales', email = 'alberto@empresa.pe', dni = '44556677') {
  fireEvent.change(screen.getByPlaceholderText('Ej. Carlos Alberto Mendoza Alarcón'), {
    target: { value: name },
  });
  fireEvent.change(screen.getByPlaceholderText('c.mendoza@empresa.com'), {
    target: { value: email },
  });
  fireEvent.change(screen.getByPlaceholderText('Ej. 45892147'), {
    target: { value: dni },
  });
  fireEvent.click(screen.getByRole('button', { name: /Pagar con izipay Online/i }));
}

try {
  console.log('--- TEST CICLO DE VIDA DE LISTENERS IZIPAY, RENDERIZADO Y PROPIEDAD GLOBAL (SDK OFICIAL) ---');

  // =========================================================================
  // SECCIÓN 1: CICLO DE VIDA BÁSICO Y ASINCRONÍA DE COLA
  // =========================================================================

  // a) Limpieza de A pendiente cuando se solicita B: B espera sin operaciones incompatibles
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    let orderNum = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      orderNum++;
      return {
        data: {
          codigoOrden: `ORD-IZI-ASYNC-00${orderNum}`,
          formToken: `token-async-00${orderNum}`,
          publicKey: 'pub-key-async',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    // Iniciar compra A
    fillAndSubmitForm('Comprador A', 'a@empresa.pe', '11223344');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-ASYNC-001');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-async-001');
    });
    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));

    // Configurar el SDK para diferir la limpieza
    sdk.deferRemoveForms = true;

    // Usuario vuelve a opciones de pago (inicia limpieza de A)
    fireEvent.click(screen.getByRole('button', { name: /Volver a opciones de pago/i }));

    // Solicitar compra B mientras la limpieza de A sigue pendiente
    fillAndSubmitForm('Comprador B', 'b@empresa.pe', '55667788');

    // B no debe haber llamado setFormToken ni registrado callbacks antes de que A termine su limpieza
    assert.equal(
      sdk.setFormTokenCalls.includes('token-async-002'),
      false,
      'B no debe llamar setFormToken mientras la limpieza previa esté pendiente'
    );
    assert.equal(
      sdk.operationsLog.filter(o => o.op === 'onSubmit').length,
      1,
      'B no debe registrar callbacks mientras la limpieza previa esté pendiente (solo existe el de A)'
    );

    // Resolver la limpieza diferida y permitir que B prosiga
    sdk.deferRemoveForms = false;
    sdk.resolveAllRemoveForms();
    await act(async () => {});

    // Ahora B concluye su inicialización de manera coordinada
    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-ASYNC-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-async-002');
      assert.ok(sdk.submitCallbacks.length >= 1, 'Callbacks de B registrados');
    });
    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));

    cleanup();
    testsPassed++;
    console.log('PASS 1: Limpieza de A pendiente cuando se solicita B: B espera sin ejecutar operaciones incompatibles.');
  }

  // b) Limpieza de A termina tarde y no borra el formulario B
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    let orderNum = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      orderNum++;
      return {
        data: {
          codigoOrden: `ORD-IZI-LATE-CLEAN-00${orderNum}`,
          formToken: `token-late-clean-${orderNum}`,
          publicKey: 'pub-key-late',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador A', 'a2@empresa.pe', '12341234');
    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LATE-CLEAN-001');
    });

    const cleanupA = window.__krCoordinator?.cleanup;
    assert.equal(typeof cleanupA, 'function');

    fireEvent.click(screen.getByRole('button', { name: /Volver a opciones de pago/i }));
    fillAndSubmitForm('Comprador B', 'b2@empresa.pe', '56785678');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LATE-CLEAN-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
    });

    const callsBefore = sdk.removeFormsCalls;

    await act(async () => {
      cleanupA();
    });

    assert.equal(sdk.removeFormsCalls, callsBefore, 'Limpieza tardía de A no debe llamar KR.removeForms');
    assert.equal(window.__krCoordinator?.isReady(), true, 'Formulario B se mantiene activo');
    assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LATE-CLEAN-002');

    cleanup();
    testsPassed++;
    console.log('PASS 2: Limpieza de A termina tarde y no borra el formulario B (B se mantiene activo y listo).');
  }

  // c) Limpieza rechaza: error capturado, mensaje en interfaz y sin inicialización ficticia
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.shouldRejectRemoveForms = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-REJECT-CLEAN',
        formToken: 'token-reject-clean',
        publicKey: 'pub-key-reject',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Rechazo', 'rechazo@empresa.pe', '77889900');

    await screen.findByText(/Fallo simulado al eliminar formularios de pasarela izipay/);

    assert.equal(sdk.setFormTokenCalls.length, 0, 'No debe llamarse a setFormToken si la limpieza falló');
    assert.equal(window.__krCoordinator?.isReady(), false, 'No debe continuarse con una inicialización ficticia');
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null);
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null);
    assert.equal(document.querySelector('.kr-payment-button'), null);
    assert.equal(sdk.submitCallbacks.length, 0, 'No debe haber callbacks registrados');
    assert.ok(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }), 'Botón de reintento visible');

    cleanup();
    testsPassed++;
    console.log('PASS 3: Limpieza rechaza: error capturado, mensaje en interfaz y sin inicialización ficticia.');
  }

  // d) Reintento posterior recupera limpiamente la sesión activa
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.shouldRejectRemoveForms = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-RETRY-001',
        formToken: 'token-retry-001',
        publicKey: 'pub-key-retry',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Retry', 'retry@empresa.pe', '99881122');

    await screen.findByText(/Fallo simulado al eliminar formularios de pasarela izipay/);
    assert.equal(window.__krCoordinator?.isReady(), false);
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null);
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null);
    assert.equal(document.querySelector('.kr-payment-button'), null);

    sdk.shouldRejectRemoveForms = false;

    fireEvent.click(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }));

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-retry-001');
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-RETRY-001');
    });

    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));
    assert.ok(screen.getByRole('button', { name: /Pagar \$ 799 USD/i }));
    assert.equal(screen.queryByText(/Fallo simulado al eliminar formularios/), null);

    cleanup();
    testsPassed++;
    console.log('PASS 4: Reintento posterior recupera limpiamente la sesión activa.');
  }

  // e) Compras consecutivas rápidas: sólo la última queda activa
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.deferSetFormToken = true;
    window.KR = sdk;

    let orderCount = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      orderCount++;
      return {
        data: {
          codigoOrden: `ORD-IZI-RAPID-00${orderCount}`,
          formToken: `token-rapid-00${orderCount}`,
          publicKey: 'pub-key-rapid',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Rapid', 'rapid1@empresa.pe', '11112222');
    await waitFor(() => {
      assert.equal(sdk._setFormTokenDeferreds.length, 1);
    });

    const btnVolverA = await screen.findByRole('button', { name: /Volver a opciones de pago/i });
    fireEvent.click(btnVolverA);
    fillAndSubmitForm('Comprador Rapid', 'rapid2@empresa.pe', '22223333');

    const btnVolverB = await screen.findByRole('button', { name: /Volver a opciones de pago/i });
    fireEvent.click(btnVolverB);
    fillAndSubmitForm('Comprador Rapid', 'rapid3@empresa.pe', '33334444');

    sdk.deferSetFormToken = false;
    sdk.resolveAllSetFormToken();
    await act(async () => {});

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-RAPID-003');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-rapid-003');
    });

    cleanup();
    testsPassed++;
    console.log('PASS 5: Compras consecutivas rápidas: sólo la última queda activa; intermedias descartadas.');
  }

  // f) Script comienza a cargar para A, A se cierra, script termina: A no ejecuta operaciones
  {
    resetEnv();
    delete window.KR;
    resetKryptonScriptLoader();

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-SCRIPT-F',
        formToken: 'token-script-f',
        publicKey: 'pub-key-script-f',
      },
    }));

    const { rerender } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Script F', 'scriptF@empresa.pe', '12121212');

    await waitFor(() => {
      assert.equal(getKryptonScriptStatus(), 'LOADING');
      assert.ok(document.getElementById('izipay-krypton-client-sdk'), 'Elemento script creado en DOM');
    });

    rerender(React.createElement(CheckoutModal, {
      isOpen: false,
      onClose: () => {},
      playbook,
    }));

    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;
    const scriptEl = document.getElementById('izipay-krypton-client-sdk');
    await act(async () => {
      if (scriptEl && typeof scriptEl.onload === 'function') {
        scriptEl.onload();
      }
    });

    assert.equal(sdk.setFormTokenCalls.length, 0, 'A no debe llamar setFormToken tras cierre');
    assert.equal(window.__krCoordinator, null, 'Coordinador debe quedar nulo');

    cleanup();
    testsPassed++;
    console.log('PASS 6: El script comienza a cargar para A, A se cierra, el script termina: A no ejecuta operaciones sobre KR.');
  }

  // g) Script comienza a cargar para A, B reemplaza a A antes de onload: sólo B se inicializa
  {
    resetEnv();
    delete window.KR;
    resetKryptonScriptLoader();

    let scriptOrderCount = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      scriptOrderCount++;
      return {
        data: {
          codigoOrden: `ORD-IZI-SCRIPT-G-00${scriptOrderCount}`,
          formToken: `token-script-g-00${scriptOrderCount}`,
          publicKey: 'pub-key-script-g',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador GA', 'ga@empresa.pe', '90909090');

    await waitFor(() => {
      assert.equal(getKryptonScriptStatus(), 'LOADING');
      assert.ok(document.getElementById('izipay-krypton-client-sdk'));
    });

    fireEvent.click(screen.getByRole('button', { name: /Volver a opciones de pago/i }));
    fillAndSubmitForm('Comprador GB', 'gb@empresa.pe', '80808080');

    assert.equal(getKryptonScriptStatus(), 'LOADING');

    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;
    const scriptEl = document.getElementById('izipay-krypton-client-sdk');
    await act(async () => {
      if (scriptEl && typeof scriptEl.onload === 'function') {
        scriptEl.onload();
      }
    });

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-SCRIPT-G-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-script-g-002');
    });

    assert.equal(sdk.setFormTokenCalls.length, 1, 'Solo B configuró token');

    cleanup();
    testsPassed++;
    console.log('PASS 7: El script comienza a cargar para A, B reemplaza a A antes de onload: sólo B se inicializa.');
  }

  // h) Fallo del script: mensaje de error con reintento limpio
  {
    resetEnv();
    delete window.KR;
    resetKryptonScriptLoader();

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-SCRIPT-H',
        formToken: 'token-script-h',
        publicKey: 'pub-key-script-h',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador H', 'h@empresa.pe', '34343434');

    await waitFor(() => {
      assert.equal(getKryptonScriptStatus(), 'LOADING');
      assert.ok(document.getElementById('izipay-krypton-client-sdk'));
    });

    const scriptEl = document.getElementById('izipay-krypton-client-sdk');
    await act(async () => {
      if (scriptEl && typeof scriptEl.onerror === 'function') {
        scriptEl.onerror(new Error('Network error'));
      }
    });

    await screen.findByText(/Error de conexión al cargar la pasarela segura izipay/);
    assert.equal(getKryptonScriptStatus(), 'FAILED');
    assert.equal(document.getElementById('izipay-krypton-client-sdk'), null);

    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    fireEvent.click(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }));

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-script-h');
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-SCRIPT-H');
    });

    assert.equal(screen.queryByText(/Error de conexión al cargar la pasarela/), null);

    cleanup();
    testsPassed++;
    console.log('PASS 8: Fallo del script: mensaje de error con reintento limpio que logra cargar y montar el formulario.');
  }

  // i) Desmontaje con operaciones pendientes: no hay fugas ni llamadas inválidas
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.deferSetFormToken = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-UNMOUNT-I',
        formToken: 'token-unmount-i',
        publicKey: 'pub-key-unmount',
      },
    }));

    const { unmount } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Unmount', 'unmount@empresa.pe', '78787878');

    await waitFor(() => {
      assert.equal(sdk._setFormTokenDeferreds.length, 1, 'Operación setFormToken pendiente en SDK');
    });

    unmount();

    assert.equal(window.__krCoordinator, null, 'Coordinador invalidado de inmediato');

    sdk.resolveSetFormToken();
    await act(async () => {});

    assert.equal(sdk.submitCallbacks.length, 0, 'No deben quedar callbacks registrados');

    cleanup();
    testsPassed++;
    console.log('PASS 9: Desmontaje con operaciones pendientes: no hay fugas ni llamadas inválidas.');
  }

  // j) Errores legítimos de la sesión activa siguen mostrándose (con o sin ID)
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-LEGIT-J',
        formToken: 'token-legit-j',
        publicKey: 'pub-key-legit',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Legit', 'legit@empresa.pe', '99887766');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LEGIT-J');
    });

    act(() => {
      sdk.dispatchError({ orderId: 'ORD-IZI-LEGIT-J', errorMessage: 'Fondos insuficientes en la tarjeta' });
    });
    await screen.findByText(/Fondos insuficientes en la tarjeta/);
    assert.ok(screen.getByText(/Fondos insuficientes en la tarjeta/));

    act(() => {
      sdk.dispatchError({ errorMessage: 'Código de seguridad (CVV) inválido' });
    });
    await screen.findByText(/Código de seguridad \(CVV\) inválido/);
    assert.ok(screen.getByText(/Código de seguridad \(CVV\) inválido/));

    cleanup();
    testsPassed++;
    console.log('PASS 10: Errores legítimos de la sesión activa siguen mostrándose (con o sin ID).');
  }

  // k) Eventos viejos de A no modifican la compra actual B
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    let orderCount = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      orderCount++;
      return {
        data: {
          codigoOrden: `ORD-IZI-STALE-K-00${orderCount}`,
          formToken: `token-stale-k-00${orderCount}`,
          publicKey: 'pub-key-stale',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador A', 'staleA@empresa.pe', '11221122');
    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-STALE-K-001');
      assert.ok(sdk.errorCallbacks.length > 0);
    });

    const oldErrorCallbackA = sdk.errorCallbacks[0];

    fireEvent.click(screen.getByRole('button', { name: /Volver a opciones de pago/i }));
    fillAndSubmitForm('Comprador B', 'staleB@empresa.pe', '22332233');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-STALE-K-002');
      assert.ok(screen.getByText('ORD-IZI-STALE-K-002'));
    });

    window.__mockLocationHref = null;

    act(() => {
      oldErrorCallbackA({ errorMessage: 'Error viejo de A sin identificador' });
    });
    assert.equal(screen.queryByText(/Error viejo de A sin identificador/), null);

    act(() => {
      sdk.dispatchError({ orderId: 'ORD-IZI-STALE-K-001', errorMessage: 'Error bancario de A con ID' });
    });
    assert.equal(screen.queryByText(/Error bancario de A con ID/), null);

    let resAjeno;
    act(() => {
      resAjeno = sdk.dispatchSubmit({ orderId: 'ORD-IZI-STALE-K-001' });
    });
    assert.equal(resAjeno, false);
    assert.equal(window.__mockLocationHref, null);

    assert.ok(screen.getByText('ORD-IZI-STALE-K-002'));
    assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-STALE-K-002');

    cleanup();
    testsPassed++;
    console.log('PASS 11: Eventos viejos de A no modifican la compra actual B (errores y submits ajenos descartados).');
  }

  // l) Submit válido de orden activa: redirige correctamente
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-SUCCESS-REDIRECT',
        formToken: 'token-success-redirect',
        publicKey: 'pub-key-success',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Exito', 'exito@empresa.pe', '87654321');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-SUCCESS-REDIRECT');
    });

    window.__mockLocationHref = null;
    act(() => {
      sdk.dispatchSubmit({ orderId: 'ORD-IZI-SUCCESS-REDIRECT' });
    });

    assert.ok(
      window.__mockLocationHref?.includes('ORD-IZI-SUCCESS-REDIRECT'),
      'La orden activa con submit válido debe redirigir a confirmación de pago'
    );

    cleanup();
    testsPassed++;
    console.log('PASS 12: Comprobación de submit válido de orden activa: redirige correctamente.');
  }

  // =========================================================================
  // SECCIÓN 2: TRATAMIENTO ESTRICTO DE FALLOS DE RENDERIZADO Y CONFIRMACIÓN DOCUMENTADA
  // =========================================================================

  // 13) Confirmación válida: muestra el estado preparado y habilita el pago
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-RENDER-OK',
        formToken: 'token-render-ok',
        publicKey: 'pub-render-ok',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Render Ok', 'renderok@empresa.pe', '11223399');

    await waitFor(() => {
      assert.equal(sdk.renderElementsCalls.length, 1);
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-RENDER-OK');
      assert.equal(window.__krCoordinator?.isReady(), true, 'La sesión debe confirmarse lista tras resolución documentada');
    });

    // Comprobación sobre el DOM visible y botones reales
    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));
    assert.ok(screen.getByRole('button', { name: /Pagar \$ 799 USD/i }));
    assert.equal(screen.queryByText(/Cargando pasarela segura izipay Online/), null);
    assert.equal(screen.queryByText(/No se pudo inicializar el formulario seguro/), null);

    cleanup();
    testsPassed++;
    console.log('PASS 13: Confirmación válida: muestra el estado preparado y habilita el pago en el DOM.');
  }

  // 14) Contenedor .kr-pan presente pero vacío, KR.formsCount ausente y renderizado rechazado: muestra error y mantiene bloqueado el pago
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.shouldRejectRenderElements = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-RENDER-FAIL',
        formToken: 'token-render-fail',
        publicKey: 'pub-render-fail',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Render Fail', 'renderfail@empresa.pe', '22334455');

    // Esperar mensaje de error en DOM
    await screen.findByText(/Fallo simulado al renderizar elementos del formulario izipay/);

    // Contenedor .kr-pan está presente en el DOM pero vacío (elemento creado por React)
    const panEl = document.querySelector('.kr-pan');
    assert.ok(panEl, 'Contenedor .kr-pan debe estar presente en el DOM');
    assert.equal(panEl.children.length, 0, 'Contenedor .kr-pan debe estar vacío (sin elementos inyectados)');

    // KR.formsCount debe estar ausente (no existe en SDK oficial)
    assert.equal(window.KR.formsCount, undefined, 'KR.formsCount debe estar ausente en el SDK real');

    // Comprobar que NINGÚN estado ficticio deja una pantalla aparentemente lista
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null, 'No debe mostrar texto de formulario preparado si renderElements falló');
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null, 'Botón de pago NO debe habilitarse si el formulario falló');
    assert.equal(document.querySelector('.kr-payment-button'), null);
    assert.equal(sdk.submitCallbacks.length, 0, 'No deben registrarse callbacks de submit');
    assert.equal(window.__krCoordinator?.isReady(), false, 'isReady debe ser false tras fallo de renderizado');
    assert.ok(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }), 'Botón de reintento debe estar disponible');

    cleanup();
    testsPassed++;
    console.log('PASS 14: Contenedor .kr-pan presente pero vacío, KR.formsCount ausente y renderizado rechazado: muestra error y mantiene bloqueado el pago.');
  }

  // 15) Error "already rendered" sin confirmación válida: no muestra éxito ni habilita pago
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.simulateAlreadyRendered = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-ALREADY-FAIL',
        formToken: 'token-already-fail',
        publicKey: 'pub-already-fail',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Already Fail', 'alreadyfail@empresa.pe', '44556677');

    // Esperar mensaje de error en DOM (no se oculta ni se traga el fallo)
    await screen.findByText(/Elements already rendered/);

    // Contenedor .kr-pan existe pero está vacío
    assert.ok(document.querySelector('.kr-pan'), 'Contenedor .kr-pan existe en el DOM');
    assert.equal(document.querySelector('.kr-pan').children.length, 0);

    // NO debe mostrar éxito, NO debe mostrar formulario preparado, NO debe habilitar botón de pago
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null, 'No debe mostrar texto de formulario preparado');
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null, 'Botón de pago NO debe habilitarse');
    assert.equal(document.querySelector('.kr-payment-button'), null);
    assert.equal(window.__krCoordinator?.isReady(), false, 'isReady debe ser false ante error already rendered sin confirmación válida');
    assert.ok(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }), 'Debe ofrecer botón de reintento');

    cleanup();
    testsPassed++;
    console.log('PASS 15: Error "already rendered" sin confirmación válida: tratado como error, sin éxito ni pago habilitado.');
  }

  // 16) Renderizado pendiente: no aparece "Formulario preparado" ni se habilita el pago
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.deferRenderElements = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-PENDING-RENDER',
        formToken: 'token-pending-render',
        publicKey: 'pub-pending-render',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Pending Render', 'pendingrender@empresa.pe', '88990011');

    await waitFor(() => {
      assert.equal(sdk._renderElementsDeferreds.length, 1);
    });

    // Mientras renderElements está diferido/pendiente:
    // 1. Indicador de carga visible en DOM
    assert.ok(screen.getByText(/Cargando pasarela segura izipay Online/));
    // 2. NO aparece texto de formulario preparado
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null, 'No debe mostrar formulario preparado mientras está pendiente');
    // 3. NO se habilita el botón de pago
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null, 'Botón de pago no debe existir en DOM mientras está pendiente');
    assert.equal(document.querySelector('.kr-payment-button'), null);
    // 4. Coordinator no está listo
    assert.equal(window.__krCoordinator?.isReady(), false, 'isReady debe ser false mientras esté pendiente');

    // Ahora resolver la promesa de renderElements
    sdk.deferRenderElements = false;
    sdk.resolveAllRenderElements();
    await act(async () => {});

    // Tras resolución documentada: aparece preparado y se habilita el botón de pago
    await waitFor(() => {
      assert.equal(window.__krCoordinator?.isReady(), true);
    });
    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));
    assert.ok(screen.getByRole('button', { name: /Pagar \$ 799 USD/i }));
    assert.equal(screen.queryByText(/Cargando pasarela segura izipay Online/), null);

    cleanup();
    testsPassed++;
    console.log('PASS 16: Renderizado pendiente: no aparece "Formulario preparado" ni botón de pago; tras confirmación se habilita.');
  }

  // 17) Reintento exitoso después del fallo
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.shouldRejectRenderElements = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-RENDER-RETRY',
        formToken: 'token-render-retry',
        publicKey: 'pub-render-retry',
      },
    }));

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Render Retry', 'retryrender@empresa.pe', '33445566');

    await screen.findByText(/Fallo simulado al renderizar elementos del formulario izipay/);
    assert.equal(window.__krCoordinator?.isReady(), false);
    assert.equal(screen.queryByText('Formulario inteligente de pago preparado'), null);
    assert.equal(screen.queryByRole('button', { name: /^Pagar \$ 799 USD/i }), null);
    assert.equal(document.querySelector('.kr-payment-button'), null);

    // Corregir estado del SDK para que renderElements tenga éxito
    sdk.shouldRejectRenderElements = false;

    // Pulsar botón de reintento
    fireEvent.click(screen.getByRole('button', { name: /Reintentar carga de pasarela/i }));

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.isReady(), true);
    });

    assert.equal(screen.queryByText(/Fallo simulado al renderizar/), null);
    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));
    assert.ok(screen.getByRole('button', { name: /Pagar \$ 799 USD/i }));

    cleanup();
    testsPassed++;
    console.log('PASS 17: Reintento exitoso después del fallo recupera formulario y estado listo.');
  }

  // 18) Confirmación o rechazo tardío de una sesión anterior: no modifica la sesión vigente
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.deferRenderElements = true;
    window.KR = sdk;

    let orderSeq = 0;
    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => {
      orderSeq++;
      return {
        data: {
          codigoOrden: `ORD-IZI-LATE-RENDER-00${orderSeq}`,
          formToken: `token-late-render-00${orderSeq}`,
          publicKey: 'pub-late-render',
        },
      };
    });

    render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    // Iniciar compra A (queda diferida en renderElements)
    fillAndSubmitForm('Comprador A', 'lateA@empresa.pe', '55667788');
    await waitFor(() => {
      assert.equal(sdk._renderElementsDeferreds.length, 1);
    });

    // Iniciar compra B mientras A sigue en vuelo
    fireEvent.click(screen.getByRole('button', { name: /Volver a opciones de pago/i }));
    fillAndSubmitForm('Comprador B', 'lateB@empresa.pe', '66778899');

    // Rechazar el diferido que pertenecía a la sesión A
    sdk.rejectRenderElements(new Error('Rechazo tardío de renderizado de sesión A'));
    await act(async () => {});

    // Comprobar que B no muestra error de A
    assert.equal(
      screen.queryByText(/Rechazo tardío de renderizado de sesión A/),
      null,
      'Rechazo tardío de A no debe contaminar la sesión B'
    );
    assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LATE-RENDER-002');

    // Ahora permitir que B resuelva su renderizado
    sdk.deferRenderElements = false;
    sdk.resolveAllRenderElements();
    await act(async () => {});

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-LATE-RENDER-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
    });

    assert.ok(screen.getByText('Formulario inteligente de pago preparado'));
    assert.ok(screen.getByRole('button', { name: /Pagar \$ 799 USD/i }));

    cleanup();
    testsPassed++;
    console.log('PASS 18: Confirmación o rechazo tardío de renderElements de A descartados sin alterar a B.');
  }

  // 19) Cierre o desmontaje mientras renderElements está pendiente
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    sdk.deferRenderElements = true;
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-CLOSE-PENDING-RENDER',
        formToken: 'token-close-render',
        publicKey: 'pub-close-render',
      },
    }));

    const { rerender } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Comprador Close Render', 'closerender@empresa.pe', '77889911');

    await waitFor(() => {
      assert.equal(sdk._renderElementsDeferreds.length, 1);
    });

    // Cerrar la modal mientras renderElements está pendiente
    rerender(React.createElement(CheckoutModal, {
      isOpen: false,
      onClose: () => {},
      playbook,
    }));

    // Resolver diferido tras cierre
    sdk.resolveAllRenderElements();
    await act(async () => {});

    assert.equal(window.__krCoordinator, null, 'Coordinador queda nulo tras cierre');
    assert.equal(sdk.submitCallbacks.length, 0, 'No deben registrarse callbacks tras cierre');

    cleanup();
    testsPassed++;
    console.log('PASS 19: Cierre con renderElements pendiente: sin excepciones ni registros huérfanos.');
  }

  // =========================================================================
  // SECCIÓN 3: PROPIEDAD GLOBAL DEL SDK ENTRE DIFERENTES INSTANCIAS (PENDIENTE 2)
  // =========================================================================

  // 19) Instancias diferentes: Montar A, desmontar A, montar B, ejecutar limpieza retenida de A
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    let instOrderNum = 0;
    postHandler = handlePostRequest(async () => {
      instOrderNum++;
      return {
        data: {
          codigoOrden: `ORD-IZI-DIFF-INST-00${instOrderNum}`,
          formToken: `token-diff-inst-00${instOrderNum}`,
          publicKey: 'pub-diff-inst',
        },
      };
    });

    // 1. Montar instancia A
    const { unmount: unmountA } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Instancia A', 'insta@empresa.pe', '10101010');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-DIFF-INST-001');
      assert.equal(window.__krCoordinator?.isReady(), true);
    });

    // Capturar la rutina de limpieza retenida de la Instancia A
    const cleanupA = window.__krCoordinator?.cleanup;
    assert.equal(typeof cleanupA, 'function');

    // Desmontar Instancia A
    unmountA();

    // 2. Montar Instancia B
    const { unmount: unmountB } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Instancia B', 'instb@empresa.pe', '20202020');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-DIFF-INST-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-diff-inst-002');
    });

    const callsBefore = sdk.removeFormsCalls;

    // 3. Ejecutar la rutina de limpieza retenida de la Instancia A
    await act(async () => {
      cleanupA();
    });

    // Comprobar que la limpieza de A NO eliminó el formulario de B
    assert.equal(
      sdk.removeFormsCalls,
      callsBefore,
      'Limpieza retenida de instancia A no debe invocar removeForms() sobre la instancia B'
    );
    assert.equal(window.__krCoordinator?.isReady(), true, 'Instancia B conserva su formulario activo');
    assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-DIFF-INST-002', 'Instancia B conserva su coordinador');
    assert.ok(sdk.submitCallbacks.length >= 1, 'Instancia B conserva sus callbacks');

    unmountB();
    cleanup();
    testsPassed++;
    console.log('PASS 20: Propiedad global inter-instancias: limpieza retenida de A no elimina formulario ni callbacks de B.');
  }

  // 21) Misma orden en ambos montajes: detecta y genera identificadores de sesión globalmente únicos
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-MISMA-ORDEN-COMPARTIDA',
        formToken: 'token-misma-orden',
        publicKey: 'pub-misma-orden',
      },
    }));

    // Montar Instancia A con la misma orden
    const { unmount: unmountA } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Mismo Código A', 'mismoA@empresa.pe', '30303030');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-MISMA-ORDEN-COMPARTIDA');
    });

    const sessionIdA = window.__krCoordinator?.sessionId;
    const cleanupA = window.__krCoordinator?.cleanup;
    assert.ok(sessionIdA);

    unmountA();

    // Montar Instancia B con exactamente el mismo código de orden
    const { unmount: unmountB } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Mismo Código B', 'mismoB@empresa.pe', '40404040');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-MISMA-ORDEN-COMPARTIDA');
      assert.equal(window.__krCoordinator?.isReady(), true);
    });

    const sessionIdB = window.__krCoordinator?.sessionId;
    assert.ok(sessionIdB);

    // Los identificadores de sesión DEBEN ser distintos a pesar de tener la misma orden
    assert.notEqual(
      sessionIdA,
      sessionIdB,
      'Los identificadores de sesión deben ser globalmente únicos entre montajes diferentes incluso con la misma orden'
    );

    const callsBefore = sdk.removeFormsCalls;

    // Ejecutar la limpieza de A
    await act(async () => {
      cleanupA();
    });

    // Como sessionIdA !== sessionIdB, la limpieza de A no debe destruir el formulario de B
    assert.equal(sdk.removeFormsCalls, callsBefore, 'Limpieza de A no debe tocar el formulario de B');
    assert.equal(window.__krCoordinator?.isReady(), true, 'B conserva su formulario montado');

    unmountB();
    cleanup();
    testsPassed++;
    console.log('PASS 21: Identificadores de sesión únicos entre distintos montajes con el mismo código de orden.');
  }

  // 22) Limpieza pendiente de A mientras B solicita inicialización
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    let pSeq = 0;
    postHandler = handlePostRequest(async () => {
      pSeq++;
      return {
        data: {
          codigoOrden: `ORD-IZI-QUEUE-INST-00${pSeq}`,
          formToken: `token-queue-inst-00${pSeq}`,
          publicKey: 'pub-queue-inst',
        },
      };
    });

    const { unmount: unmountA } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Pendiente A', 'pendA@empresa.pe', '50505050');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-QUEUE-INST-001');
    });

    // Diferir removeForms para que la limpieza de A quede en vuelo
    sdk.deferRemoveForms = true;

    // Desmontar A (inicia coordinateAsyncCleanup de A en vuelo diferido)
    unmountA();

    // Montar B e iniciar inicialización
    const { unmount: unmountB } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Pendiente B', 'pendB@empresa.pe', '60606060');

    // B espera en cola a que la limpieza de A termine
    assert.equal(
      sdk.setFormTokenCalls.includes('token-queue-inst-002'),
      false,
      'B no debe inicializarse mientras la limpieza previa en cola esté pendiente'
    );

    // Resolver la limpieza diferida de A
    sdk.deferRemoveForms = false;
    sdk.resolveAllRemoveForms();
    await act(async () => {});

    // B prosigue y concluye exitosamente
    await waitFor(() => {
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-QUEUE-INST-002');
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(sdk.currentFormToken, 'token-queue-inst-002');
    });

    unmountB();
    cleanup();
    testsPassed++;
    console.log('PASS 22: Limpieza pendiente de A encola a B sin interferencia y B se inicializa limpiamente.');
  }

  // 23) Limpieza de A cuando no existe B: sus recursos huérfanos se eliminan limpiamente
  {
    resetEnv();
    const sdk = new SimulatedKryptonSdk();
    window.KR = sdk;

    getHandler = async () => ({ data: [account] });
    postHandler = handlePostRequest(async () => ({
      data: {
        codigoOrden: 'ORD-IZI-ORPHAN-CLEAN',
        formToken: 'token-orphan-clean',
        publicKey: 'pub-orphan-clean',
      },
    }));

    const { unmount: unmountA } = render(React.createElement(CheckoutModal, {
      isOpen: true,
      onClose: () => {},
      playbook,
    }));

    fillAndSubmitForm('Usuario Huerfano', 'orphan@empresa.pe', '70707070');

    await waitFor(() => {
      assert.equal(window.__krCoordinator?.isReady(), true);
      assert.equal(window.__krCoordinator?.activeOrderCode, 'ORD-IZI-ORPHAN-CLEAN');
    });

    const callsBefore = sdk.removeFormsCalls;

    // Desmontar A sin que exista ninguna otra instancia
    unmountA();

    await waitFor(() => {
      assert.equal(sdk.currentFormToken, null, 'Los recursos huérfanos de A deben eliminarse');
      assert.ok(sdk.removeFormsCalls > callsBefore, 'removeForms() debe haberse ejecutado para A');
    });

    const ownership = getGlobalSdkOwnership();
    assert.equal(ownership.activeOwnerSessionId, null, 'Propiedad global queda liberada');
    assert.equal(ownership.activeRequestedSessionId, null, 'Sesión solicitada queda nula');

    cleanup();
    testsPassed++;
    console.log('PASS 23: Limpieza de A cuando no existe B: recursos huérfanos eliminados y propiedad liberada.');
  }

  console.log(`\nRESULTADO CICLO DE VIDA, RENDERIZADO Y PROPIEDAD GLOBAL IZIPAY: ${testsPassed}/23 escenarios aprobados con éxito.`);
} finally {
  cleanup();
  axios.post = originalPost;
  axios.get = originalGet;
  dom.window.close();
  if (fs.existsSync(compiledPath)) fs.unlinkSync(compiledPath);
}
