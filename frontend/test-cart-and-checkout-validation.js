import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import axios from 'axios';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
  url: 'http://localhost/',
});
globalThis.window = dom.window;
globalThis.document = dom.window.document;
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

// Interceptar axios para pruebas de servicio y errores controlados
const originalPost = axios.post;
const originalGet = axios.get;
let postHandler = async () => ({ data: {} });
let getHandler = async () => ({ data: [] });
axios.post = (...args) => postHandler(...args);
axios.get = (...args) => getHandler(...args);
const originalCreate = axios.create;
axios.create = () => ({
  get: (...args) => getHandler(...args),
  post: (...args) => postHandler(...args),
  interceptors: { request: { use: () => {} }, response: { use: () => {} } },
});

// Compilar módulos requeridos con esbuild
const cartContextPath = path.resolve(import.meta.dirname, 'src/context/CartContext.tsx');
const compiledCartContextPath = path.resolve(import.meta.dirname, '.test-compiled-CartContext.mjs');
const compiledCtx = esbuild.buildSync({
  entryPoints: [cartContextPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios'],
  loader: { '.css': 'empty' },
});
fs.writeFileSync(compiledCartContextPath, compiledCtx.outputFiles[0].text, 'utf8');
const cartContextModule = await import(pathToFileURL(compiledCartContextPath).href);
const {
  CartProvider,
  useCart,
  isPlaybookPurchasable,
  getItemPrice,
} = cartContextModule;

const cartDrawerPath = path.resolve(import.meta.dirname, 'src/components/marketplace/CartDrawer.tsx');
const compiledCartDrawerPath = path.resolve(import.meta.dirname, '.test-compiled-CartDrawer.mjs');
const compiledDrawer = esbuild.buildSync({
  entryPoints: [cartDrawerPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios', 'lucide-react'],
  loader: { '.css': 'empty' },
});
fs.writeFileSync(compiledCartDrawerPath, compiledDrawer.outputFiles[0].text, 'utf8');
const cartDrawerModule = await import(pathToFileURL(compiledCartDrawerPath).href);
const { CartDrawer } = cartDrawerModule;

const storeServiceModule = await import(pathToFileURL(path.resolve(import.meta.dirname, 'src/services/storeService.ts')).href);
const { storeService } = storeServiceModule;
sessionStorage.setItem('lysandri_checkout_intent_sandbox_guest_old@example.invalid_1001', '{"email":"old@example.invalid"}');
sessionStorage.setItem('lysandri_checkout_intent_sandbox_local_1001', '{"email":""}');
storeService.clearSandboxCheckoutIntents();
assert.equal(
  sessionStorage.getItem('lysandri_checkout_intent_sandbox_guest_old@example.invalid_1001'),
  null,
  'Legacy sandbox checkout intents containing buyer data should be cleared'
);
assert.equal(
  sessionStorage.getItem('lysandri_checkout_intent_sandbox_local_1001'),
  null,
  'Old local sandbox checkout intents should be cleared'
);
let proceededItems = null;
const CartHarness = () => {
  const cart = useCart();
  return React.createElement(
    React.Fragment,
    null,
    React.createElement('output', { 'data-testid': 'verification-state' }, cart.verificationStatus),
    React.createElement('output', { 'data-testid': 'cart-programs' }, cart.cartItems.map((item) => item.title).join('|')),
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: cart.cartItems,
      onRemoveItem: cart.removeFromCart,
      onClearCart: cart.clearCart,
      verificationStatus: cart.verificationStatus,
      verificationError: cart.verificationError,
      onRetryVerification: cart.retryVerification,
      onProceedToCheckout: (items) => { proceededItems = items; },
    })
  );
};

console.log('='.repeat(80));
console.log('PRUEBAS DE VALIDACIÓN: Carrito, Contratos de Compra, Idempotencia y Cursos de Muestra');
console.log('='.repeat(80));

// -----------------------------------------------------------------------------
// ESCENARIO 1: PROGRAMA REAL HABILITADO
// -----------------------------------------------------------------------------
{
  const realProgram = {
    id: '1',
    programId: 1,
    title: 'Programa de Alta Dirección en Transformación Digital',
    price: 1250,
    currency: 'USD',
    tier: 'ENTERPRISE',
    category: 'Estrategia',
    tags: ['Liderazgo', 'Digital'],
    isSample: false,
    isDemo: false,
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
    disponibilidadVerificada: true,
  };

  assert.equal(
    isPlaybookPurchasable(realProgram),
    true,
    'El programa real habilitado debe ser marcado como comprable'
  );

  assert.equal(
    getItemPrice(realProgram),
    1250,
    'El precio debe obtenerse de los datos reales del programa (1250 USD), no del tier (950 USD)'
  );

  let proceedItems = null;
  const { container } = render(
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: [realProgram],
      onRemoveItem: () => {},
      onClearCart: () => {},
      onProceedToCheckout: (items) => {
        proceedItems = items;
      },
      verificationStatus: 'VERIFIED',
      verificationError: '',
      onRetryVerification: () => {},
    })
  );

  // Comprobar visualización con precio real y moneda formateada
  assert.ok(
    container.textContent.includes('1.250,00') || container.textContent.includes('1,250.00'),
    'El precio real de 1250 USD debe visualizarse en la tarjeta del carrito'
  );

  // No debe mostrar alert de curso de muestra
  assert.equal(
    container.querySelector('.lx-cart-sample-alert'),
    null,
    'No debe existir cartel de advertencia de curso de muestra para un programa real'
  );

  // El botón debe estar activo
  const checkoutBtn = container.querySelector('footer.lx-cart-summary button.lx-overlay-primary');
  assert.ok(checkoutBtn, 'El botón de checkout debe existir en el resumen');
  assert.equal(checkoutBtn.disabled, false, 'El botón de checkout debe estar habilitado para programas reales');

  // Al hacer clic debe invocar onProceedToCheckout con los ítems reales
  fireEvent.click(checkoutBtn);
  assert.ok(proceedItems && proceedItems.length === 1, 'Debe transferir la orden hacia el flujo de checkout');
  assert.equal(proceedItems[0].programId, 1);

  cleanup();
  console.log('✅ ESCENARIO 1 APROBADO: Programa real habilitado se añade con precio autorizado (1250 USD) y continúa al pago');
}

// -----------------------------------------------------------------------------
// ESCENARIO 2: CURSO FICTICIO / DE MUESTRA (ID 1001 "Inteligencia Artificial para Directivos & C-Suite")
// -----------------------------------------------------------------------------
{
  const sampleProgram = {
    id: 'sample-1',
    programId: 1001,
    title: 'Inteligencia Artificial para Directivos & C-Suite',
    price: 499,
    currency: 'USD',
    tier: 'ENTERPRISE',
    category: 'Inteligencia Artificial',
    tags: ['IA', 'C-Suite'],
    isSample: true,
    isDemo: true,
  };

  assert.equal(
    isPlaybookPurchasable(sampleProgram),
    false,
    'El curso de muestra (isSample: true, id: 1001) debe ser marcado como NO comprable'
  );

  let submittedIds = null;
  postHandler = async (_url, data) => {
    submittedIds = data.programaIds;
    return { data: { codigoOrden: 'ORD-ID-1001' } };
  };
  await storeService.createCheckoutSession({
    programaIds: [1001],
    email: 'test@example.com',
    nombreCompleto: 'Test User',
    tipoComprobante: 'BOLETA',
    numeroDocumento: '12345678',
  });
  assert.deepEqual(submittedIds, [1001], 'No debe existir un umbral de ID artificial en el servicio');

  console.log('✅ ESCENARIO 2 APROBADO: Curso de muestra bloqueado por procedencia sin reservar el ID 1001');
}

// -----------------------------------------------------------------------------
// ESCENARIO 3: PROGRAMA NO DISPONIBLE O NO ACTIVO
// -----------------------------------------------------------------------------
{
  const inactiveProgram = {
    id: '2',
    programId: 2,
    title: 'Programa Inactivo de Auditoría',
    price: 800,
    currency: 'USD',
    activo: false,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: false,
    disponibilidadVerificada: true,
  };
  const eligibleProgram = {
    ...inactiveProgram,
    activo: true,
    disponibleParaCompra: true,
  };

  assert.equal(
    isPlaybookPurchasable(inactiveProgram),
    false,
    'Un programa con activo: false debe ser marcado como NO comprable'
  );
  assert.equal(isPlaybookPurchasable({ ...eligibleProgram, disponibleParaCompra: undefined }), false);
  assert.equal(isPlaybookPurchasable({ ...eligibleProgram, price: undefined }), false);
  assert.equal(isPlaybookPurchasable({ ...eligibleProgram, currency: undefined }), false);

  const nonExistentIdProgram = {
    id: '0',
    programId: 0,
    title: 'Programa Invalido',
  };

  assert.equal(
    isPlaybookPurchasable(nonExistentIdProgram),
    false,
    'Un programa con ID 0 o no positivo debe ser rechazado'
  );

  console.log('✅ ESCENARIO 3 APROBADO: Programas inactivos o con identificador inválido rechazados');
}

// -----------------------------------------------------------------------------
// ESCENARIO 4: CARRITO ANTIGUO EN localStorage CON CURSO DE MUESTRA (ID 1001)
// -----------------------------------------------------------------------------
{
  // Simular localStorage con datos antiguos antes de la corrección
  const oldCartItems = [
    {
      id: 'sample-1',
      programId: 1001,
      title: 'Inteligencia Artificial para Directivos & C-Suite',
      tier: 'ENTERPRISE',
      category: 'Inteligencia Artificial',
    },
  ];
  localStorage.setItem('lysandri_cart_items', JSON.stringify(oldCartItems));

  let removedId = null;
  const { container } = render(
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: oldCartItems,
      onRemoveItem: (id) => {
        removedId = id;
      },
      onClearCart: () => {},
      verificationStatus: 'VERIFIED',
      verificationError: '',
      onRetryVerification: () => {},
    })
  );

  // 1. La muestra debe explicarse de forma neutral, sin presentarse como un error
  const sampleNote = container.querySelector('.lx-cart-sample-note');
  assert.ok(sampleNote, 'Debe explicar de forma neutral el contenido de muestra');
  assert.equal(sampleNote.getAttribute('role'), 'note');
  assert.match(sampleNote.textContent, /no forman parte de una compra/);
  assert.equal(container.querySelector('[role="alert"].lx-cart-sample-note'), null);

  // 2. El artículo debe tener el badge de muestra
  const sampleBadge = container.querySelector('.lx-cart-badge-sample');
  assert.ok(sampleBadge, 'El artículo en la lista debe mostrar el badge de muestra');

  // 3. El botón "Continuar al pago" debe estar deshabilitado
  const checkoutBtn = container.querySelector('footer.lx-cart-summary button.lx-overlay-primary');
  assert.ok(checkoutBtn, 'El botón de checkout debe existir');
  assert.equal(checkoutBtn.disabled, true, 'El botón de checkout debe estar bloqueado mientras haya cursos de muestra');

  // 4. El botón "Retirar programas de muestra" debe funcionar
  const removeSampleBtn = sampleNote.querySelector('button');
  assert.ok(removeSampleBtn, 'Debe existir el botón para retirar programas de muestra');
  fireEvent.click(removeSampleBtn);
  assert.equal(removedId, 1001, 'Debe invocar onRemoveItem con el ID 1001 del curso de muestra');

  cleanup();
  localStorage.clear();
  console.log('✅ ESCENARIO 4 APROBADO: Carrito antiguo con curso ficticio detectado, botón deshabilitado y acción de retiro funcional');
}

// -----------------------------------------------------------------------------
// ESCENARIO 5: FALLO DE CONEXIÓN Y RECUPERACIÓN DEL BOTÓN
// -----------------------------------------------------------------------------
{
  const realProgram = {
    id: '3',
    programId: 3,
    title: 'CFO Leadership & Estrategia Financiera',
    price: 990,
    currency: 'USD',
    isSample: false,
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
    disponibilidadVerificada: true,
  };

  let checkoutAttemptCount = 0;
  let cartWiped = false;
  const { container } = render(
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: [realProgram],
      onRemoveItem: () => {},
      onClearCart: () => {
        cartWiped = true;
      },
      onProceedToCheckout: () => {
        checkoutAttemptCount++;
        throw new Error('No se pudo abrir el formulario');
      },
      verificationStatus: 'VERIFIED',
      verificationError: '',
      onRetryVerification: () => {},
    })
  );

  const checkoutBtn = container.querySelector('footer.lx-cart-summary button.lx-overlay-primary');
  assert.ok(checkoutBtn);

  // El fallo en la transición al formulario no debe vaciar el carrito.
  await act(async () => {
    fireEvent.click(checkoutBtn);
  });

  // Comprobar que el carrito NO se vació ante el error transitorio
  assert.equal(cartWiped, false, 'El carrito NO debe vaciarse ante un fallo transitorio de red');

  // Comprobar que se muestra el mensaje de error
  const errorMsg = container.querySelector('.lx-overlay-error');
  assert.ok(errorMsg, 'Debe desplegarse el mensaje de error de transición');
  assert.match(errorMsg.textContent, /No se pudo abrir el formulario/);

  // Comprobar que el botón se recuperó y no quedó bloqueado indefinidamente
  assert.equal(checkoutBtn.disabled, false, 'El botón debe quedar habilitado para permitir reintentar');

  cleanup();
  assert.equal(checkoutAttemptCount, 1);
  console.log('✅ ESCENARIO 5 APROBADO: Fallo al abrir checkout conserva el carrito y recupera el botón');
}

// -----------------------------------------------------------------------------
// ESCENARIO 6: IDEMPOTENCIA Y NO DUPLICACIÓN DE ÓRDENES AL REINTENTAR
// -----------------------------------------------------------------------------
{
  const buyerScope = 'test_buyer_idempotent';
  const programId = 1;

  // Generar clave de idempotencia
  const key1 = storeService.getOrCreateIdempotencyKey(buyerScope, programId);
  assert.ok(key1 && key1.length > 0, 'Debe generar una clave de idempotencia');

  // Persistir intento simulando primer envío
  storeService.saveCheckoutIntent({
    claveIdempotencia: key1,
    programId,
    buyerScope,
    email: 'buyer@example.com',
    nombreCompleto: 'Buyer Name',
    tipoComprobante: 'BOLETA',
    numeroDocumento: '77778888',
    metodoPago: 'IZIPAY',
    codigoOrden: 'ORD-IDEMPOTENT-001',
    status: 'REGISTRADA',
    createdAt: Date.now(),
  });

  // Reintento: debe recuperar exactamente la misma clave y orden
  const key2 = storeService.getOrCreateIdempotencyKey(buyerScope, programId);
  assert.equal(key1, key2, 'Al reintentar debe mantener la misma clave de idempotencia para evitar duplicar órdenes');

  const intent = storeService.getCheckoutIntent(buyerScope, programId);
  assert.equal(intent?.codigoOrden, 'ORD-IDEMPOTENT-001', 'Debe recuperar la orden previa registrada');

  storeService.clearCheckoutIntent(buyerScope, programId);
  console.log('✅ ESCENARIO 6 APROBADO: Clave de idempotencia preservada entre reintentos para evitar órdenes duplicadas');
}

// -----------------------------------------------------------------------------
// ESCENARIO 7: TEXTO EXACTO DE MÉTODOS DE PAGO EN EL FOOTER
// -----------------------------------------------------------------------------
{
  const realProgram = {
    id: '1',
    programId: 1,
    title: 'Transformación Digital',
    price: 1250,
    currency: 'USD',
    isSample: false,
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
    disponibilidadVerificada: true,
  };

  const { container } = render(
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: [realProgram],
      onRemoveItem: () => {},
      onClearCart: () => {},
      verificationStatus: 'VERIFIED',
      verificationError: '',
      onRetryVerification: () => {},
    })
  );

  const footerText = container.querySelector('.lx-cart-payment-methods-info');
  assert.ok(footerText, 'Debe existir el párrafo informativo de métodos de pago');
  assert.ok(
    footerText.textContent.includes('Izipay') &&
      footerText.textContent.includes('transferencia bancaria') &&
      footerText.textContent.includes('Yape / Plin'),
    'El texto debe describir fielmente los métodos admitidos (Izipay, transferencia, Yape/Plin)'
  );
  assert.equal(
    container.textContent.includes('El pago se completará en una página externa'),
    false,
    'El texto desactualizado de redirección a página externa debe haber sido eliminado'
  );

  cleanup();
  console.log('✅ ESCENARIO 7 APROBADO: Mensaje de métodos de pago verificado (Izipay, transferencia oficial, Yape/Plin)');
}

// -----------------------------------------------------------------------------
// ESCENARIO 8: PREVENCIÓN DE DOBLE SUBMIT CONCURRENTE AL PULSAR RÁPIDAMENTE
// -----------------------------------------------------------------------------
{
  const realProgram = {
    id: '1',
    programId: 1,
    title: 'Transformación Digital',
    price: 1250,
    currency: 'USD',
    isSample: false,
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
    disponibilidadVerificada: true,
  };
  const secondProgram = {
    ...realProgram,
    id: '2',
    programId: 2,
    title: 'Segundo programa',
    price: 250,
  };

  let proceedCount = 0;
  let proceededSelection = [];
  const { container } = render(
    React.createElement(CartDrawer, {
      isOpen: true,
      onClose: () => {},
      cartItems: [realProgram, secondProgram],
      onRemoveItem: () => {},
      onClearCart: () => {},
      verificationStatus: 'VERIFIED',
      verificationError: '',
      onRetryVerification: () => {},
      onProceedToCheckout: () => {
        proceedCount++;
        proceededSelection = [realProgram, secondProgram];
      },
    })
  );

  const checkoutBtn = container.querySelector('footer.lx-cart-summary button.lx-overlay-primary');
  // Dos clics dentro de la misma actualización prueban el bloqueo inmediato, no solo el disabled posterior.
  act(() => {
    fireEvent.click(checkoutBtn);
    fireEvent.click(checkoutBtn);
  });
  assert.equal(proceedCount, 1, 'Dos clics mientras la transición está pendiente deben producir un solo checkout');
  assert.deepEqual(proceededSelection.map((item) => item.programId), [1, 2]);

  cleanup();
  console.log('✅ ESCENARIO 8 APROBADO: Solicitudes de checkout coordinadas sin dobles envíos');
}

// ESCENARIO 12: RECHAZO MULTIMONEDA SIN ELIMINAR ARTÍCULOS
{
  const shared = {
    title: 'Programa comercial',
    price: 500,
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
    disponibilidadVerificada: true,
  };
  const mixedItems = [
    { ...shared, id: '201', programId: 201, currency: 'USD' },
    { ...shared, id: '202', programId: 202, currency: 'PEN' },
  ];
  let proceeded = false;
  let cleared = false;
  const { container } = render(React.createElement(CartDrawer, {
    isOpen: true,
    onClose: () => {},
    cartItems: mixedItems,
    onRemoveItem: () => {},
    onClearCart: () => { cleared = true; },
    verificationStatus: 'VERIFIED',
    verificationError: '',
    onRetryVerification: () => {},
    onProceedToCheckout: () => { proceeded = true; },
  }));
  assert.ok(container.querySelector('.lx-cart-mixed-currency-alert'));
  assert.equal(container.querySelector('footer.lx-cart-summary button.lx-overlay-primary').disabled, true);
  assert.equal(mixedItems.length, 2);
  assert.equal(proceeded, false);
  assert.equal(cleared, false);
  cleanup();
  console.log('✅ ESCENARIO 12 APROBADO: Monedas mixtas bloquean el checkout sin perder artículos');
}

// ESCENARIO 9: RECUPERACIÓN DE localStorage, PRECIO AUTORIZADO E ID REAL > 1000
{
  const id = 1201;
  localStorage.setItem('lysandri_cart_items', JSON.stringify([{
    id: String(id),
    programId: id,
    title: 'Precio anterior',
    price: 99,
    currency: 'USD',
    activo: true,
    precioAprobado: true,
    moodleVerificado: true,
    disponibleParaCompra: true,
  }]));
  proceededItems = null;
  getHandler = async (url) => {
    assert.match(url, /programas\/disponibilidad-compra\?ids=1201/);
    return { data: [{
      idPrograma: id,
      titulo: 'Programa real 1201',
      precio: 1250,
      moneda: 'PEN',
      activo: true,
      precioAprobado: true,
      esGratuito: false,
      moodleCourseId: 2201,
      moodleVerificado: true,
      disponibleParaCompra: true,
    }] };
  };
  const { container } = render(React.createElement(CartProvider, null, React.createElement(CartHarness)));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'VERIFIED'));
  await waitFor(() => assert.match(screen.getByTestId('cart-programs').textContent, /Programa real 1201/));
  assert.match(container.textContent, /1,250\.00|1\.250,00/);
  const button = document.querySelector('footer.lx-cart-summary button.lx-overlay-primary');
  assert.equal(button.disabled, false, 'Un programa activo con ID > 1000 debe permitir checkout después de confirmarlo');
  act(() => fireEvent.click(button));
  assert.deepEqual(proceededItems.map((item) => item.programId), [1201]);
  cleanup();
  localStorage.clear();
  console.log('✅ ESCENARIO 9 APROBADO: CartProvider recupera localStorage, actualiza importe y permite el ID real 1201');
}

// ESCENARIO 10: PROGRAMA DESACTIVADO DESPUÉS DE GUARDARLO
{
  localStorage.setItem('lysandri_cart_items', JSON.stringify([{
    id: '1202', programId: 1202, title: 'Programa guardado', price: 800, currency: 'USD',
  }]));
  getHandler = async () => ({ data: [{
    idPrograma: 1202, titulo: 'Programa guardado', precio: 800, moneda: 'USD',
    activo: false, precioAprobado: true, esGratuito: false, moodleVerificado: false,
    disponibleParaCompra: false, motivoNoDisponible: 'Programa desactivado después de guardarlo',
  }] });
  render(React.createElement(CartProvider, null, React.createElement(CartHarness)));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'VERIFIED'));
  assert.match(document.body.textContent, /Programa desactivado después de guardarlo/);
  assert.ok(document.querySelector('button[aria-label="Quitar Programa guardado del carrito"]'));
  fireEvent.click(document.querySelector('button[aria-label="Quitar Programa guardado del carrito"]'));
  await waitFor(() => assert.equal(screen.getByTestId('cart-programs').textContent, ''));
  cleanup();
  localStorage.clear();
  console.log('✅ ESCENARIO 10 APROBADO: Programa desactivado se explica y puede retirarse sin vaciar otros artículos');
}

// ESCENARIO 11: RESPUESTA EXACTA VACÍA Y ERROR DE RED SON ESTADOS DISTINTOS
{
  localStorage.setItem('lysandri_cart_items', JSON.stringify([{
    id: '1203', programId: 1203, title: 'Programa recuperado', price: 300, currency: 'USD',
  }]));
  getHandler = async () => ({ data: [] });
  render(React.createElement(CartProvider, null, React.createElement(CartHarness)));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'VERIFIED'));
  assert.match(document.body.textContent, /consulta comercial exacta/);
  assert.equal(document.querySelector('footer.lx-cart-summary button.lx-overlay-primary').disabled, true);
  cleanup();

  const networkError = new axios.AxiosError('Network Error');
  getHandler = async () => { throw networkError; };
  render(React.createElement(CartProvider, null, React.createElement(CartHarness)));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'ERROR'));
  assert.match(document.body.textContent, /No se pudo conectar con el servidor/);
  assert.match(screen.getByTestId('cart-programs').textContent, /Programa recuperado/);
  cleanup();

  const notFoundError = new axios.AxiosError('Request failed with status code 404');
  notFoundError.response = {
    data: {},
    status: 404,
    statusText: 'Not Found',
    headers: {},
    config: { headers: {} },
  };
  getHandler = async () => { throw notFoundError; };
  render(React.createElement(CartProvider, null, React.createElement(CartHarness)));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'ERROR'));
  assert.match(document.body.textContent, /El backend no reconoce la verificación de compra/);
  assert.match(document.body.textContent, /GET \/api\/v1\/programas\/disponibilidad-compra/);
  assert.match(screen.getByTestId('cart-programs').textContent, /Programa recuperado/);
  assert.equal(document.querySelector('.lx-cart-badge-sample').textContent, 'Verificación pendiente');
  assert.doesNotMatch(document.querySelector('.lx-cart-items').textContent, /No disponible/);
  assert.ok(screen.getByRole('button', { name: /Reintentar verificación/i }));
  assert.equal(document.querySelector('footer.lx-cart-summary button.lx-overlay-primary').disabled, true);

  getHandler = async () => ({ data: [{
    idPrograma: 1203, titulo: 'Programa recuperado', precio: 300, moneda: 'USD',
    activo: true, precioAprobado: true, esGratuito: false, moodleVerificado: true,
    disponibleParaCompra: true,
  }] });
  fireEvent.click(screen.getByRole('button', { name: /Reintentar verificación/i }));
  await waitFor(() => assert.equal(screen.getByTestId('verification-state').textContent, 'VERIFIED'));
  assert.equal(document.querySelector('footer.lx-cart-summary button.lx-overlay-primary').disabled, false);
  cleanup();
  localStorage.clear();
  console.log('✅ ESCENARIO 11 APROBADO: Consulta vacía marca no disponible; red falla de forma reintentable y conserva el carrito');
}

// Limpieza de archivos temporales de compilación de pruebas
try {
  fs.unlinkSync(compiledCartContextPath);
  fs.unlinkSync(compiledCartDrawerPath);
  axios.create = originalCreate;
} catch {
  // Ignorar limpieza
}

console.log('='.repeat(80));
console.log('RESUMEN DE PRUEBAS: 12 de 12 escenarios aprobados con éxito (100% PASS)');
console.log('CÓDIGO DE SALIDA: 0 (EXIT CODE 0)');
console.log('='.repeat(80));
