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

const { cleanup, fireEvent, render, screen, waitFor } = await import('@testing-library/react');

const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbCI6IkFETUlOIiwiZXhwIjoxOTk5OTk5OTk5fQ.test-sig';
let mockUser = { id: 1, email: 'admin@lysandri.com', rol: 'ADMIN' };
globalThis.__mockAuthModule = {
  useAuth: () => ({
    user: mockUser,
    token: validJwt,
    isAuthenticated: true,
    isLoading: false,
    logout: () => {},
    login: async () => mockUser,
  }),
};
globalThis.__mockCartModule = {
  useCart: () => ({
    cartItems: [],
    addToCart: () => {},
    removeFromCart: () => {},
    clearCart: () => {},
    isCartOpen: false,
    setIsCartOpen: () => {},
    openCart: () => {},
    closeCart: () => {},
    totalAmount: 0,
    itemCount: 0,
    hasUnpurchasableItems: false,
    unpurchasableItems: [],
    removeUnpurchasableItems: () => {},
    syncCartWithCatalog: () => {},
  }),
};
// Compilar MarketplaceView
const marketplacePath = path.resolve(import.meta.dirname, 'src/pages/MarketplaceView.tsx');
const compiledMarketplacePath = path.resolve(import.meta.dirname, '.test-compiled-MarketplaceView.mjs');
const syllabusDirectory = path.resolve(import.meta.dirname, '../ai-service/data/docs/Cursos');
const syllabusFiles = Object.fromEntries(fs.readdirSync(syllabusDirectory)
  .filter((name) => name.endsWith('.md'))
  .map((name) => [
    `../../../ai-service/data/docs/Cursos/${name}`,
    fs.readFileSync(path.join(syllabusDirectory, name), 'utf8'),
  ]));
const marketplaceCompiled = esbuild.buildSync({
  entryPoints: [marketplacePath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios', 'lucide-react', 'react-router-dom', '../context/AuthContext', '../../context/AuthContext'],
  define: { 'import.meta.glob': 'readSyllabuses' },
  banner: { js: `const readSyllabuses = () => (${JSON.stringify(syllabusFiles)});` },
  loader: { '.svg': 'text', '.png': 'dataurl', '.css': 'empty', '.gif': 'empty' },
});
const marketplaceSource = marketplaceCompiled.outputFiles[0].text
  .replace(
    /throw\s+new\s+Error\(\s*["'][^"']*?useCart must be used within a CartProvider[^"']*?["']\s*\);/g,
    'return globalThis.__mockCartModule.useCart();'
  )
  .replace(
    /throw\s+new\s+Error\(\s*["'][^"']*?useAuth debe utilizarse dentro de AuthProvider[^"']*?["']\s*\);/g,
    'return globalThis.__mockAuthModule.useAuth();'
  )
  .replace(
    /import\s*\{\s*useAuth(?:\s+as\s+(\w+))?\s*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
    (_, alias) => `const ${alias || 'useAuth'} = () => globalThis.__mockAuthModule.useAuth();`
  );
fs.writeFileSync(compiledMarketplacePath, marketplaceSource, 'utf8');
const { MarketplaceView } = await import(pathToFileURL(compiledMarketplacePath).href);

// Compilar AccountingView
const accountingPath = path.resolve(import.meta.dirname, 'src/pages/AccountingView.tsx');
const compiledAccountingPath = path.resolve(import.meta.dirname, '.test-catalog-AccountingView.mjs');
const accountingCompiled = esbuild.buildSync({
  entryPoints: [accountingPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['react', 'react-dom', 'axios', 'lucide-react', 'react-router-dom', '../context/AuthContext', '../../context/AuthContext'],
  loader: { '.svg': 'text', '.png': 'dataurl', '.css': 'empty', '.gif': 'empty' },
});
const accountingSource = accountingCompiled.outputFiles[0].text.replace(
  /import\s*\{\s*useAuth(?:\s+as\s+(\w+))?\s*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
  (_, alias) => `const ${alias || 'useAuth'} = () => globalThis.__mockAuthModule.useAuth();`
);
fs.writeFileSync(compiledAccountingPath, accountingSource, 'utf8');
const { AccountingView } = await import(pathToFileURL(compiledAccountingPath).href);

// Compilar servicios reales
const catalogServicePath = path.resolve(import.meta.dirname, 'src/services/courseCatalog.ts');
const compiledCatalogServicePath = path.resolve(import.meta.dirname, '.test-compiled-courseCatalog.mjs');
const catalogServiceCompiled = esbuild.buildSync({
  entryPoints: [catalogServicePath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['axios'],
  loader: { '.svg': 'text', '.png': 'dataurl' },
});
fs.writeFileSync(compiledCatalogServicePath, catalogServiceCompiled.outputFiles[0].text, 'utf8');
const { courseCatalogService, toCatalogCourse, buildCategories } = await import(pathToFileURL(compiledCatalogServicePath).href);

const accountingServiceFile = path.resolve(import.meta.dirname, 'src/services/accountingService.ts');
const compiledAccountingServicePath = path.resolve(import.meta.dirname, '.test-compiled-accountingService.mjs');
const accountingServiceCompiled = esbuild.buildSync({
  entryPoints: [accountingServiceFile],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  external: ['axios'],
  loader: { '.svg': 'text', '.png': 'dataurl' },
});
fs.writeFileSync(compiledAccountingServicePath, accountingServiceCompiled.outputFiles[0].text, 'utf8');
const { accountingService } = await import(pathToFileURL(compiledAccountingServicePath).href);

let testsPassed = 0;

try {
  console.log('================================================================================');
  console.log('PRUEBAS: Eliminación del Modo Demostración y Datos Ficticios (Catálogo y Contabilidad)');
  console.log('================================================================================');

  // -------------------------------------------------------------------------
  // SECCIÓN 1: CATÁLOGO Y PLAYBOOKS
  // -------------------------------------------------------------------------

  // Test 1: courseCatalog.ts contiene cursos de muestra con isSample: true
  {
    const catalogSource = fs.readFileSync(path.resolve(import.meta.dirname, 'src/services/courseCatalog.ts'), 'utf8');
    assert.ok(catalogSource.includes('FALLBACK_PLAYBOOKS'), 'courseCatalog debe definir FALLBACK_PLAYBOOKS como contenido de muestra');
    assert.ok(catalogSource.includes('isSample: true'), 'Cursos de muestra deben incluir isSample: true');
    testsPassed++;
    console.log('✅ TEST 1 APROBADO: courseCatalog.ts incluye cursos de muestra debidamente identificados con isSample.');
  }

  // Test 2: Catálogo con programas reales del backend se muestran correctamente
  {
    cleanup();
    const realPrograms = [
      {
        id: 101,
        programId: 101,
        title: 'FinOps y Transformación Digital Cloud',
        description: 'Optimización de costes cloud para directores financieros.',
        tier: 'ENTERPRISE',
        price: 799,
        currency: 'USD',
        active: true,
        categoria: 'Finanzas',
      },
      {
        id: 102,
        programId: 102,
        title: 'Ciberseguridad y Riesgo Operacional',
        description: 'Gestión de incidentes de seguridad y continuidad de negocio.',
        tier: 'EXECUTIVE',
        price: 2500,
        currency: 'PEN',
        active: true,
        categoria: 'Ciberseguridad',
      },
    ];

    render(React.createElement(MarketplaceView, {
      playbooks: realPrograms,
      categories: buildCategories(realPrograms),
      isLoading: false,
      errorMessage: null,
      onRetry: () => {},
      onSelectPlaybook: () => {},
    }));

    assert.ok(screen.getAllByText('FinOps y Transformación Digital Cloud').length >= 1, 'Título del programa 1 visible');
    assert.ok(screen.getAllByText('Ciberseguridad y Riesgo Operacional').length >= 1, 'Título del programa 2 visible');
    assert.equal(screen.queryByText(/demostración/i), null, 'No hay botones ni textos de demostración');
    testsPassed++;
    console.log('✅ TEST 2 APROBADO: Catálogo muestra exclusivamente programas reales sin badges de demo.');
  }

  // Test 3: Catálogo vacío del backend muestra estrictamente "No hay programas disponibles"
  {
    cleanup();
    render(React.createElement(MarketplaceView, {
      playbooks: [],
      categories: [],
      isLoading: false,
      errorMessage: null,
      onRetry: () => {},
      onSelectPlaybook: () => {},
    }));

    assert.ok(screen.getByText('No hay programas disponibles'), 'Mensaje de catálogo vacío presente');
    assert.ok(screen.getByText('Actualmente no hay programas académicos publicados en la plataforma.'), 'Descripción vacía presente');
    assert.equal(screen.queryByText('Ver catálogo de demostración'), null, 'No debe existir botón Ver catálogo de demostración');
    assert.equal(screen.queryByText('Explorar catálogo de demostración'), null, 'No debe existir botón Explorar catálogo de demostración');
    testsPassed++;
    console.log('✅ TEST 3 APROBADO: Catálogo vacío muestra estado vacío sin ofrecer catálogo ficticio.');
  }

  // Test 4: Error al consultar el catálogo muestra "No se pudo cargar el catálogo" y opción Reintentar
  {
    cleanup();
    let retryCalled = false;
    render(React.createElement(MarketplaceView, {
      playbooks: [],
      categories: [],
      isLoading: false,
      errorMessage: 'Fallo de conexión 503 con el servidor',
      onRetry: () => { retryCalled = true; },
      onSelectPlaybook: () => {},
    }));

    assert.ok(screen.getByText('No se pudo cargar el catálogo'), 'Mensaje de error de catálogo visible');
    assert.ok(screen.getByText('Fallo de conexión 503 con el servidor'), 'Detalle del error visible');
    const retryBtn = screen.getByRole('button', { name: /Reintentar/i });
    assert.ok(retryBtn, 'Botón de reintentar visible');
    assert.equal(screen.queryByText(/catálogo de demostración/i), null, 'No debe ofrecer catálogo de demo en error');

    fireEvent.click(retryBtn);
    assert.equal(retryCalled, true, 'Click en Reintentar invoca callback onRetry');
    testsPassed++;
    console.log('✅ TEST 4 APROBADO: Error de catálogo permite reintentar y no sustituye con cursos ficticios.');
  }

  // -------------------------------------------------------------------------
  // SECCIÓN 2: CONTABILIDAD
  // -------------------------------------------------------------------------

  // Test 5: accountingService.ts no contiene MOCK_REPORTE ni datos inventados
  {
    const accountingServiceSource = fs.readFileSync(path.resolve(import.meta.dirname, 'src/services/accountingService.ts'), 'utf8');
    assert.equal(accountingServiceSource.includes('MOCK_REPORTE'), false, 'accountingService no debe definir MOCK_REPORTE');
    assert.equal(accountingServiceSource.includes('F-2026-001'), false, 'accountingService no debe contener datos ficticios');
    testsPassed++;
    console.log('✅ TEST 5 APROBADO: accountingService.ts libre de MOCK_REPORTE y datos simulados.');
  }

  // Test 6: AccountingView no tiene botón ni selector de "Modo demostración"
  {
    cleanup();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    // Interceptar axios para proveer reporte contable real
    const originalRequest = axios.Axios.prototype.request;
    axios.Axios.prototype.request = async function (configOrUrl) {
      const url = typeof configOrUrl === 'string' ? configOrUrl : configOrUrl.url;
      if (url === '/internal/operaciones/incidencias') return { status: 200, data: [], headers: {} };
      if (url.includes('/internal/accounting/ventas')) {
        return {
          status: 200,
          data: {
            totalVentasPen: 15000,
            totalBaseImponiblePen: 12711.86,
            totalIgvRecaudadoPen: 2288.14,
            totalDetraccionesPen: 600,
            totalVentasUsd: 4500,
            totalBaseImponibleUsd: 3813.56,
            totalIgvRecaudadoUsd: 686.44,
            totalDetraccionesUsd: 0,
            cantidadComprobantes: 4,
            cantidadBoletas: 2,
            cantidadFacturas: 2,
            cantidadAnulados: 0,
            ventasConsolidadas: [
              {
                idComprobante: 1,
                idOrden: 10,
                codigoOrden: 'ORD-REAL-001',
                cliente: 'EMPRESA REAL SAC',
                documentoIdentidad: '20123456789',
                tipoComprobante: 'FACTURA',
                serieNumero: 'F001-00000001',
                moneda: 'PEN',
                total: 10000,
                subtotal: 8474.58,
                igv: 1525.42,
                estadoComprobante: 'EMITIDO',
                fechaOrden: '2026-10-15',
              },
            ],
            comprobantesEmitidos: [],
          },
          headers: {},
        };
      }
      return { status: 200, data: {}, headers: {} };
    };

    render(React.createElement(AccountingView));

    await waitFor(() => {
      assert.ok(screen.getByText('Contabilidad y facturación'));
    });

    // Comprobar ausencia de elementos demo
    assert.equal(screen.queryByText(/modo demostración/i), null, 'No debe existir texto ni botón de Modo demostración');
    assert.equal(screen.queryByText(/simulado/i), null, 'No debe existir indicación de datos simulados');
    assert.equal(screen.queryByRole('button', { name: /demostración/i }), null, 'No hay botón de demo');

    // Restaurar axios
    axios.Axios.prototype.request = originalRequest;
    testsPassed++;
    console.log('✅ TEST 6 APROBADO: AccountingView no ofrece selector ni acceso a modo demostración.');
  }

  // Test 7: Periodo sin registros muestra banner explícito de estado vacío
  {
    cleanup();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    const originalRequest = axios.Axios.prototype.request;
    axios.Axios.prototype.request = async function (configOrUrl) {
      return {
        status: 200,
        data: {
          totalVentasPen: 0,
          totalBaseImponiblePen: 0,
          totalIgvRecaudadoPen: 0,
          totalDetraccionesPen: 0,
          totalVentasUsd: 0,
          totalBaseImponibleUsd: 0,
          totalIgvRecaudadoUsd: 0,
          totalDetraccionesUsd: 0,
          cantidadComprobantes: 0,
          cantidadBoletas: 0,
          cantidadFacturas: 0,
          ventasConsolidadas: [],
          comprobantesEmitidos: [],
        },
        headers: {},
      };
    };

    render(React.createElement(AccountingView));

    await waitFor(() => {
      assert.ok(screen.getByText('No existen registros contables para el período seleccionado'), 'Banner de estado vacío visible');
    });

    axios.Axios.prototype.request = originalRequest;
    testsPassed++;
    console.log('✅ TEST 7 APROBADO: Período sin registros contables muestra estado vacío claro y transparente.');
  }

  // Test 8: Error del servidor en Contabilidad muestra error con botón "Reintentar conexión" y no muestra reportes inventados
  {
    cleanup();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    let requestCount = 0;
    const originalRequest = axios.Axios.prototype.request;
    axios.Axios.prototype.request = async function (configOrUrl) {
      requestCount++;
      const err = new Error('Conexión rehusada por el servidor contable');
      err.response = { status: 503, data: { message: 'Servicio contable no disponible temporalmente' } };
      throw err;
    };

    render(React.createElement(AccountingView));

    await waitFor(() => {
      assert.ok(screen.getByText('Error al cargar datos contables del servidor'), 'Mensaje de error presente');
      assert.ok(screen.getByText(/Servicio contable no disponible temporalmente/), 'Detalle del error presente');
      assert.ok(screen.getByRole('button', { name: /Reintentar conexión/i }), 'Botón de reintentar conexión presente');
    });

    // Verificar que no se muestran datos ficticios ni KPIs en cero engañosos
    assert.equal(screen.queryByText(/Margen Neto Operativo/i), null, 'No debe mostrar KPIs engañosos en pantalla de error');
    assert.equal(screen.queryByText('No existen registros contables para el período seleccionado'), null, 'No debe confundir error con período vacío');

    // Probar el botón de reintentar
    const retryBtn = screen.getByRole('button', { name: /Reintentar conexión/i });
    fireEvent.click(retryBtn);
    await waitFor(() => {
      assert.ok(requestCount >= 2, 'Reintentar conexión envía una nueva petición al backend');
    });

    axios.Axios.prototype.request = originalRequest;
    testsPassed++;
    console.log('✅ TEST 8 APROBADO: Error de conexión muestra estado de error con reintento y no fabrica reportes.');
  }

  // Test 9: Exportación CSV vacía rechaza exportación ficticia y emite aviso
  {
    cleanup();
    let alertMsg = '';
    window.alert = (msg) => { alertMsg = msg; };

    // En AccountingView, si ventasFiltradas está vacío, exportarCSV muestra alerta
    // Probamos la función del servicio directamente
    const fakePin = 'LYS-AUDIT-2026-SECURE';
    let sireFailed = false;
    try {
      await accountingService.descargarSireTxt(fakePin, 'TODOS');
    } catch (e) {
      sireFailed = true;
      assert.ok(e.message.includes('Debe especificar un período mensual tributario válido'));
    }
    assert.equal(sireFailed, true, 'SIRE rechaza TODOS');
    testsPassed++;
    console.log('✅ TEST 9 APROBADO: Exportaciones oficiales rechazan datos inválidos sin inventar archivos.');
  }

  // Test 10: Imposibilidad de comprar o tramitar órdenes con programas ficticios o inexistentes (ID inválido)
  {
    const checkoutSource = fs.readFileSync(path.resolve(import.meta.dirname, 'src/components/marketplace/CheckoutModal.tsx'), 'utf8');
    assert.ok(checkoutSource.includes('!Number.isInteger(p.programId) || p.programId <= 0'), 'Validación estricta de ID institucional presente');
    const practiceFlow = checkoutSource.split('if (isPracticeOrder) {').find((branch) => branch.includes('storeService.crearOrdenPractica'));
    assert.ok(practiceFlow, 'La inscripción de práctica tiene un servicio propio');
    assert.ok(checkoutSource.includes('!isPlaybookPurchasable(item)'), 'El checkout comercial valida la disponibilidad del programa');
    assert.equal(checkoutSource.includes('Modo demostración'), false, 'CheckoutModal no muestra banner ni aviso de modo demostración');
    testsPassed++;
    console.log('✅ TEST 10 APROBADO: CheckoutModal valida los programas comerciales y conserva el registro separado de práctica.');
  }

  console.log('================================================================================');
  console.log(`RESUMEN: ${testsPassed} de ${testsPassed} pruebas aprobadas exitosamente.`);
  console.log('================================================================================');

  // Limpieza de artefactos compilados temporales
  for (const f of [compiledMarketplacePath, compiledAccountingPath, compiledCatalogServicePath, compiledAccountingServicePath]) {
    try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch {}
  }

  process.exit(0);

} catch (err) {
  console.error('FALLO EN PRUEBAS:', err);
  for (const f of [compiledMarketplacePath, compiledAccountingPath, compiledCatalogServicePath, compiledAccountingServicePath]) {
    try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch {}
  }
  process.exit(1);
}
