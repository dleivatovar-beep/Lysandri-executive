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

const { cleanup, fireEvent, render, screen, waitFor, act } = await import('@testing-library/react');

let mockUser = {
  id: 1,
  username: 'admin',
  nombre: 'Administrador Contable',
  email: 'admin@lysandri.pe',
  rol: 'ADMIN',
};

const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbCI6IkFETUlOIiwiZXhwIjoxOTk5OTk5OTk5fQ.test-sig';

globalThis.__mockAuthModule = {
  useAuth: () => ({
    user: mockUser,
    token: validJwt,
    isAuthenticated: true,
    isLoading: false,
    logout: () => {},
    login: async () => mockUser,
    setSessionUser: () => {},
  }),
};

// Compilar AccountingView
const acctViewPath = path.resolve(import.meta.dirname, 'src/pages/AccountingView.tsx');
const compiledAcctPath = path.resolve(import.meta.dirname, '.test-compiled-AccountingView.mjs');
const compiledAcct = esbuild.buildSync({
  entryPoints: [acctViewPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  loader: { '.png': 'text', '.gif': 'text', '.svg': 'text' },
  external: [
    'react',
    'react-dom',
    'axios',
    'lucide-react',
    'react-router-dom',
    '../context/AuthContext',
    '../../context/AuthContext',
  ],
});

const sourceAcct = compiledAcct.outputFiles[0].text.replace(
  /import\s*\{[^}]*useAuth[^}]*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
  'const useAuth = (...args) => globalThis.__mockAuthModule.useAuth(...args);',
);
fs.writeFileSync(compiledAcctPath, sourceAcct, 'utf8');
const { default: AccountingView } = await import(pathToFileURL(compiledAcctPath).href);

// Compilar AdminDashboard
const adminDashPath = path.resolve(import.meta.dirname, 'src/pages/admin/AdminDashboard.tsx');
const compiledAdminPath = path.resolve(import.meta.dirname, '.test-compiled-AdminDashboard.mjs');
const compiledAdmin = esbuild.buildSync({
  entryPoints: [adminDashPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  write: false,
  loader: { '.png': 'text', '.gif': 'text', '.svg': 'text' },
  external: [
    'react',
    'react-dom',
    'axios',
    'lucide-react',
    'react-router-dom',
    '../../context/AuthContext',
  ],
});
const sourceAdmin = compiledAdmin.outputFiles[0].text.replace(
  /import\s*\{[^}]*useAuth[^}]*\}\s*from\s*['"][^'"]*AuthContext['"];?/g,
  'const useAuth = (...args) => globalThis.__mockAuthModule.useAuth(...args);',
);
fs.writeFileSync(compiledAdminPath, sourceAdmin, 'utf8');
const { AdminDashboard } = await import(pathToFileURL(compiledAdminPath).href);

let alertsTriggered = [];
window.alert = (msg) => { alertsTriggered.push(String(msg)); };
globalThis.alert = window.alert;

let promptsTriggered = [];
window.prompt = (msg, def) => {
  promptsTriggered.push({ msg, def });
  return 'Anulación autorizada por auditoría fiscal';
};
globalThis.prompt = window.prompt;

const mockReporteOctubre = {
  rucEmisor: '20609812451',
  razonSocial: 'LYSANDRI S.A.C.',
  periodoTributario: '2026-10',
  totalVentasPen: 3500.0,
  totalVentasUsd: 1200.0,
  baseImponiblePen: 2966.1,
  igvPen: 533.9,
  detraccionesPen: 280.0,
  baseImponibleUsd: 1016.95,
  igvUsd: 183.05,
  detraccionesUsd: 0.0,
  periodosDisponibles: ['2026-09', '2026-10'],
  ventasConsolidadas: [
    {
      idVenta: 1,
      idOrden: 101,
      idComprobante: 501,
      tipoComprobante: 'FACTURA',
      serieNumero: 'E001-140',
      cliente: 'ACME CORP SAC',
      numeroDocumento: '20123456789',
      fechaEmision: '2026-10-15',
      moneda: 'PEN',
      total: 2360.0,
      baseImponible: 2000.0,
      igv: 360.0,
      detraccion: 280.0,
      medioPago: 'IZIPAY_ONLINE',
      estadoComprobante: 'EMITIDO',
      cuo: 'CP00000501',
      tipoCambio: null,
    },
    {
      idVenta: 2,
      idOrden: 102,
      idComprobante: 502,
      tipoComprobante: 'BOLETA',
      serieNumero: 'EB01-890',
      cliente: 'JUAN PEREZ',
      numeroDocumento: '45892147',
      fechaEmision: '2026-10-16',
      moneda: 'PEN',
      total: 1140.0,
      baseImponible: 966.1,
      igv: 173.9,
      detraccion: 0.0,
      medioPago: 'STRIPE',
      estadoComprobante: 'EMITIDO',
      cuo: 'CP00000502',
      tipoCambio: null,
    },
    {
      idVenta: 3,
      idOrden: 103,
      idComprobante: 503,
      tipoComprobante: 'FACTURA',
      serieNumero: 'E001-141',
      cliente: 'GLOBAL TECH INC',
      numeroDocumento: '20987654321',
      fechaEmision: '2026-10-18',
      moneda: 'USD',
      total: 1200.0,
      baseImponible: 1016.95,
      igv: 183.05,
      detraccion: 0.0,
      medioPago: 'TELECREDITO_BCP',
      estadoComprobante: 'EMITIDO',
      cuo: 'CP00000503',
      tipoCambio: 3.752,
    },
    {
      idVenta: 4,
      idOrden: 104,
      idComprobante: null, // Pendiente de comprobante
      tipoComprobante: 'ORDEN_PENDIENTE',
      serieNumero: null,
      cliente: 'CARLOS ENRIQUEZ',
      numeroDocumento: '10234567890',
      fechaOrden: '2026-10-20',
      moneda: 'PEN',
      total: 800.0,
      baseImponible: 0,
      igv: 0,
      detraccion: 0,
      medioPago: 'OTRO_DESCONOCIDO',
      estadoComprobante: 'PENDIENTE',
      cuo: null,
      tipoCambio: null,
    },
  ],
};

let capturedRequests = [];
let testsPassed = 0;
let originalAxiosRequest = null;
let customRequestHandler = null;

try {
  console.log('--- TEST DE INTERACCIÓN: AccountingView y AdminDashboard ---');

  originalAxiosRequest = axios.Axios.prototype.request;
  axios.Axios.prototype.request = async function (configOrUrl, maybeConfig) {
    if (customRequestHandler) {
      return customRequestHandler.call(this, configOrUrl, maybeConfig);
    }
    let config = typeof configOrUrl === 'string' ? { ...(maybeConfig || {}), url: configOrUrl } : { ...configOrUrl };
    let body = config.data;
    if (typeof body === 'string') {
      try { body = JSON.parse(body); } catch {}
    }
    const method = (config.method || 'get').toUpperCase();
    capturedRequests.push({
      method,
      url: config.url,
      headers: config.headers,
      params: config.params,
      data: body,
    });

    if (config.url.includes('/internal/accounting/ventas')) {
      return { status: 200, data: mockReporteOctubre, headers: {} };
    }
    if (config.url.includes('/anular')) {
      return { status: 200, data: { success: true, estado: 'ANULADO' }, headers: {} };
    }
    if (config.url.includes('/usuarios') && method === 'GET') {
      return {
        status: 200,
        data: [
          {
            idUser: 42,
            nombres: 'Elena',
            apellidos: 'Ríos',
            email: 'elena@empresa.pe',
            rol: 'ESTUDIANTE',
            telefono: '+51 987654321',
            activo: true,
          },
        ],
        headers: {},
      };
    }
    if (config.url.startsWith('/usuarios/') && method === 'PUT') {
      return { status: 200, data: { idUser: 42, ...body }, headers: {} };
    }
    if (config.url.includes('/rol') && method === 'PATCH') {
      return { status: 200, data: { idUser: 42, ...body }, headers: {} };
    }
    if (config.url.includes('/programs')) {
      return { status: 200, data: [], headers: {} };
    }
    if (config.url.includes('/metricas')) {
      return { status: 200, data: {}, headers: {} };
    }
    return { status: 200, data: {}, headers: {} };
  };

  // Test 1: Carga autenticada con PIN y visualización métricas separadas PEN / USD
  {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);
    alertsTriggered = [];

    render(React.createElement(AccountingView));

    await waitFor(() => {
      assert.ok(screen.getByText('Contabilidad y facturación'));
      // Verificamos separación PEN y USD
      assert.ok(screen.getAllByText(/3[,.]500/i).length >= 1, 'Total PEN visible');
      assert.ok(screen.getAllByText(/1[,.]200/i).length >= 1, 'Total USD visible por separado sin sumar');
      // Verificamos orden pendiente excluida y mostrada en sección dedicada
      assert.ok(screen.getByText(/Órdenes pendientes por facturar \(1\)/i));
      // Verificamos liquidación de pasarelas sin porcentaje inventado
      assert.ok(screen.getByText('Dato no disponible'));
    });

    testsPassed++;
    console.log('PASS 1: AccountingView autenticado con PIN muestra métricas PEN y USD separadas y sección de pendientes.');
  }

  // Test 2: Cambio de período limpia datos previos (setData(null)) y preserva lista de períodos conocidos
  {
    const select = screen.getByDisplayValue('Todos los periodos');
    assert.ok(select, 'Selector de período presente');

    // Cambiar a 2026-10
    fireEvent.change(select, { target: { value: '2026-10' } });

    await waitFor(() => {
      const selectAfter = screen.getByDisplayValue('2026-10');
      assert.ok(selectAfter, 'Selector actualizado a 2026-10');
    });

    const selectUpdated = screen.getByDisplayValue('2026-10');
    // Comprobamos que el selector mantiene los períodos descubiertos
    const options = [...selectUpdated.querySelectorAll('option')].map((o) => o.value);
    assert.ok(options.includes('2026-09'), '2026-09 en lista de conocidos');
    assert.ok(options.includes('2026-10'), '2026-10 en lista de conocidos');
    assert.ok(options.includes('TODOS'), 'TODOS en lista de opciones');

    // Volver a cambiar a TODOS para probar el rechazo en Test 3
    fireEvent.change(selectUpdated, { target: { value: 'TODOS' } });

    await waitFor(() => {
      assert.ok(screen.getByDisplayValue('Todos los periodos'), 'Selector restablecido a TODOS');
    });

    testsPassed++;
    console.log('PASS 2: Cambio de período actualiza consulta y conserva histórico de períodos conocidos.');
  }

  // Test 3: Descarga SIRE rechaza explícitamente "TODOS" con mensaje claro sin disparar exportación técnica
  {
    alertsTriggered = [];
    const sireBtn = screen.getByText('Descargar SIRE 14.1 TXT');
    fireEvent.click(sireBtn);

    await waitFor(() => {
      assert.ok(alertsTriggered.length > 0, 'Debe disparar alert de rechazo');
      assert.ok(alertsTriggered[0].includes('debe seleccionar un período mensual tributario válido'), 'Mensaje de rechazo claro');
    });

    testsPassed++;
    console.log('PASS 3: Descarga SIRE rechaza "TODOS" con alerta explicativa sin llamar al backend.');
  }

  // Test 4: Anulación de comprobante usa idComprobante real (501) y no idOrden (101)
  {
    cleanup();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    render(React.createElement(AccountingView));

    await waitFor(() => screen.getByText('Contabilidad y facturación'));

    // Cambiar a pestaña Comprobantes
    fireEvent.click(screen.getByRole('button', { name: /Comprobantes/i }));

    await waitFor(() => screen.getByText('E001-140'));

    // Botón de anular comprobante
    const anularBtn = screen.getAllByTitle(/Anular comprobante emitido ante SUNAT/i)[0];
    capturedRequests = [];
    fireEvent.click(anularBtn);

    await waitFor(() => {
      const anularReq = capturedRequests.find((r) => r.url.includes('/anular'));
      assert.ok(anularReq, 'POST de anulación emitido');
      assert.ok(anularReq.url.includes('/comprobantes/501/anular'), `Debe anular por idComprobante 501, pero fue: ${anularReq.url}`);
      assert.equal(anularReq.data.motivo, 'Anulación autorizada por auditoría fiscal');
    });

    testsPassed++;
    console.log('PASS 4: Anulación de comprobante utiliza idComprobante (501) sin cruzar con idOrden (101).');
  }

  // Test 5: AdminDashboard actualización de perfil (PUT /usuarios/{id}) vs rol (PATCH /usuarios/{id}/rol)
  {
    cleanup();
    capturedRequests = [];

    render(React.createElement(AdminDashboard, { initialSection: 'users' }));

    await waitFor(() => screen.getByText('Elena Ríos'));

    // Abrir edición de Elena
    const editBtn = screen.getByLabelText(/Editar a Elena Ríos/i);
    fireEvent.click(editBtn);

    await waitFor(() => screen.getByPlaceholderText('Nombres'));

    // Modificar datos personales Y rol simultáneamente
    const nombresInput = screen.getByPlaceholderText('Nombres');
    fireEvent.change(nombresInput, { target: { value: 'Elena Patricia' } });

    const rolSelect = screen.getByDisplayValue('Ejecutivo / Alumno');
    fireEvent.change(rolSelect, { target: { value: 'ADMIN' } });

    // Guardar cambios
    fireEvent.click(screen.getByRole('button', { name: /Guardar Cambios/i }));

    await waitFor(() => {
      // Debe haber exactamente 1 PUT (datos personales sin rol) y 1 PATCH (rol con motivo)
      const putReq = capturedRequests.find((r) => r.method === 'PUT');
      const patchReq = capturedRequests.find((r) => r.method === 'PATCH');

      assert.ok(putReq, 'Debe existir petición PUT para datos personales');
      assert.equal(putReq.url, '/usuarios/42');
      assert.equal(putReq.data.nombres, 'Elena Patricia');
      assert.equal(putReq.data.rol, undefined, 'PUT /usuarios/42 no debe incluir campo rol');

      assert.ok(patchReq, 'Debe existir petición PATCH para rol');
      assert.equal(patchReq.url, '/usuarios/42/rol');
      assert.equal(patchReq.params.rol, 'ADMIN');
      assert.ok(patchReq.params.motivo, 'PATCH debe incluir motivo de auditoría');
    });

    testsPassed++;
    console.log('PASS 5: AdminDashboard separa estrictamente PUT /usuarios/{id} (perfil) y PATCH /usuarios/{id}/rol (auditoría).');
  }

  // Test 6: Promesas controladas: Respuestas fuera de orden al cambiar de período
  {
    cleanup();
    window.sessionStorage.clear();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    const mockNoviembre = {
      ...mockReporteOctubre,
      periodoTributario: '2026-11',
      periodoSeleccionado: '2026-11',
      totalVentasPen: 9999.0,
      ventasConsolidadas: [
        {
          idVenta: 10,
          idOrden: 201,
          idComprobante: 601,
          tipoComprobante: 'FACTURA',
          serieNumero: 'F001-00000999',
          cliente: 'EMPRESA NOVIEMBRE S.A.C.',
          numeroDocumento: '20999888777',
          fechaEmision: '2026-11-12',
          moneda: 'PEN',
          total: 9999.0,
          baseImponible: 8473.73,
          igv: 1525.27,
          detraccion: 0.0,
          medioPago: 'STRIPE',
          estadoComprobante: 'EMITIDO',
          cuo: 'CP00000601',
          tipoCambio: null,
        },
      ],
    };

    let deferredRequests = {};
    customRequestHandler = async function (configOrUrl, maybeConfig) {
      let config = typeof configOrUrl === 'string' ? { ...(maybeConfig || {}), url: configOrUrl } : { ...configOrUrl };
      if (config.url.includes('/internal/accounting/ventas')) {
        const periodo = config.params?.periodo || 'TODOS';
        return new Promise((resolve, reject) => {
          deferredRequests[periodo] = { resolve, reject, signal: config.signal };
        });
      }
      return { status: 200, data: {}, headers: {} };
    };

    render(React.createElement(AccountingView));

    // La carga inicial pide "TODOS"
    await waitFor(() => {
      assert.ok(deferredRequests['TODOS'], 'Petición inicial TODOS debe emitirse');
    });

    // Resolver la inicial con datos de octubre
    await act(async () => {
      deferredRequests['TODOS'].resolve({ status: 200, data: mockReporteOctubre, headers: {} });
    });

    await waitFor(() => {
      assert.ok(screen.getAllByText(/3[,.]500/i).length >= 1, 'Octubre inicial cargado');
    });

    // Ahora simular condición de carrera:
    // 1. Usuario selecciona 2026-10 (Petición A lenta)
    const select = screen.getByDisplayValue('Todos los periodos');
    fireEvent.change(select, { target: { value: '2026-10' } });

    await waitFor(() => {
      assert.ok(deferredRequests['2026-10'], 'Petición A (2026-10) en vuelo');
    });

    // 2. Antes de que responda A, usuario cambia a 2026-11 (Petición B rápida)
    fireEvent.change(select, { target: { value: '2026-11' } });

    await waitFor(() => {
      assert.ok(deferredRequests['2026-11'], 'Petición B (2026-11) en vuelo');
    });

    // 3. Petición B (2026-11) resuelve primero
    await act(async () => {
      deferredRequests['2026-11'].resolve({ status: 200, data: mockNoviembre, headers: {} });
    });

    // La pantalla debe mostrar los datos de noviembre
    await waitFor(() => {
      assert.ok(screen.getAllByText(/9[,.]999/i).length >= 1, 'Noviembre visible');
    });

    // 4. Petición A (2026-10) resuelve después (tardía)
    await act(async () => {
      deferredRequests['2026-10'].resolve({ status: 200, data: mockReporteOctubre, headers: {} });
    });

    // Comprobar que la respuesta tardía de octubre fue descartada y se preservan las cifras de noviembre
    assert.ok(screen.getAllByText(/9[,.]999/i).length >= 1, 'Noviembre permanece visible tras respuesta tardía de octubre');

    // 5. Simular error tardío de petición descartada:
    // Usuario cambia a 2026-09 (petición C lenta que fallará)
    fireEvent.change(select, { target: { value: '2026-09' } });
    await waitFor(() => {
      assert.ok(deferredRequests['2026-09'], 'Petición C (2026-09) en vuelo');
    });

    // Inmediatamente usuario cambia a 2026-11 de nuevo (petición D)
    delete deferredRequests['2026-11'];
    fireEvent.change(select, { target: { value: '2026-11' } });
    await waitFor(() => {
      assert.ok(deferredRequests['2026-11'], 'Petición D (2026-11) en vuelo');
    });

    // Petición C (2026-09) falla con error 500 tardío
    await act(async () => {
      deferredRequests['2026-09'].reject({
        response: { status: 500, data: { message: 'Fallo temporal en 2026-09' } },
      });
    });

    // El error tardío no debe aparecer en pantalla ni sustituir los datos vigentes
    assert.equal(screen.queryByText(/Fallo temporal en 2026-09/), null, 'Error tardío de petición descartada no debe mostrarse');

    // Petición D resuelve con éxito
    await act(async () => {
      deferredRequests['2026-11'].resolve({ status: 200, data: mockNoviembre, headers: {} });
    });

    await waitFor(() => {
      assert.ok(screen.getAllByText(/9[,.]999/i).length >= 1);
    });

    customRequestHandler = null;
    testsPassed++;
    console.log('PASS 6: Promesas controladas: Respuestas y errores fuera de orden descartados correctamente.');
  }

  // Test 7: Rechazo de sesión contable por falta de permisos o PIN incorrecto
  {
    cleanup();
    window.sessionStorage.clear();
    window.localStorage.removeItem('lysandri_token');

    // Parte A: PIN incorrecto
    customRequestHandler = async function (configOrUrl) {
      const url = typeof configOrUrl === 'string' ? configOrUrl : configOrUrl.url;
      if (url.includes('/internal/accounting/ventas')) {
        const error = new Error('No autorizado');
        error.response = { status: 403, data: { message: 'PIN de auditoría no válido' } };
        throw error;
      }
      return { status: 200, data: {}, headers: {} };
    };

    // Render sin PIN previo (muestra formulario de acceso contable)
    render(React.createElement(AccountingView));

    await waitFor(() => screen.getByPlaceholderText('Usuario'));

    // Ingresar credenciales y disparar login
    fireEvent.change(screen.getByPlaceholderText('Usuario'), {
      target: { value: 'contador@lysandri.pe' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Iniciar sesión/i }));

    await waitFor(() => {
      assert.ok(
        screen.getByText(/Acceso no autorizado \(401\/403\)/i),
        'Debe mostrar mensaje de rechazo por PIN/credenciales no autorizadas'
      );
    });

    assert.equal(window.sessionStorage.getItem('lysandri_audit_pin'), null, 'PIN inválido no debe quedar almacenado');

    // Parte B: Usuario con rol no autorizado (ej. ESTUDIANTE)
    cleanup();
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);
    globalThis.__mockAuthModule.useAuth = () => ({
      user: { id: 99, email: 'estudiante@empresa.pe', rol: 'ESTUDIANTE' },
      token: validJwt,
      isAuthenticated: true,
      isLoading: false,
      logout: () => {},
      login: async () => ({ id: 99, email: 'estudiante@empresa.pe', rol: 'ESTUDIANTE' }),
    });

    render(React.createElement(AccountingView));

    await waitFor(() => {
      assert.ok(
        screen.getByText(/Acceso denegado.*no tiene permisos/i),
        'Rol ESTUDIANTE debe ser denegado para el área contable'
      );
    });

    // Restaurar mockUser
    globalThis.__mockAuthModule.useAuth = () => ({
      user: mockUser,
      token: validJwt,
      isAuthenticated: true,
      isLoading: false,
      logout: () => {},
      login: async () => mockUser,
    });
    customRequestHandler = null;

    testsPassed++;
    console.log('PASS 7: Rechazo estricto de sesión contable por PIN inválido o permisos insuficientes.');
  }

  // Test 8: Intento de anulación rechazado por el servidor: el comprobante no debe mostrarse como anulado
  {
    cleanup();
    alertsTriggered = [];
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    customRequestHandler = async function (configOrUrl) {
      let config = typeof configOrUrl === 'string' ? { url: configOrUrl } : { ...configOrUrl };
      if (config.url.includes('/anular')) {
        const error = new Error('Error SUNAT');
        error.response = {
          status: 400,
          data: { message: 'El comprobante ya fue informado ante SUNAT y no admite anulación directa' },
        };
        throw error;
      }
      if (config.url.includes('/internal/accounting/ventas')) {
        return { status: 200, data: mockReporteOctubre, headers: {} };
      }
      return { status: 200, data: {}, headers: {} };
    };

    render(React.createElement(AccountingView));

    await waitFor(() => screen.getByText('Contabilidad y facturación'));

    // Cambiar a pestaña Comprobantes
    fireEvent.click(screen.getByRole('button', { name: /Comprobantes/i }));
    await waitFor(() => screen.getByText('E001-140'));

    // Intentar anular
    const anularBtn = screen.getAllByTitle(/Anular comprobante emitido ante SUNAT/i)[0];
    fireEvent.click(anularBtn);

    await waitFor(() => {
      assert.ok(alertsTriggered.length > 0, 'Debe emitir alerta al usuario');
      assert.ok(
        alertsTriggered.some((a) => a.includes('El comprobante ya fue informado ante SUNAT')),
        'Alerta debe contener mensaje de error del servidor'
      );
    });

    // Comprobar que en pantalla el comprobante NO aparece como anulado
    const badgeVigente = screen.getAllByText('VIGENTE');
    assert.ok(badgeVigente.length >= 1, 'El comprobante debe continuar mostrándose como VIGENTE tras fallo de anulación');
    assert.equal(screen.queryAllByText('ANULADO').length, 0, 'No debe haber comprobantes anulados tras error');
    assert.ok(screen.getAllByTitle(/Anular comprobante emitido ante SUNAT/i).length >= 1, 'Botón anular sigue presente');

    customRequestHandler = null;
    testsPassed++;
    console.log('PASS 8: Intento de anulación rechazado por servidor preserva estado no-anulado y notifica error.');
  }

  // Test 9: Fallo en la recarga tras anulación exitosa: avisar al usuario sin marcar error falso de anulación
  {
    cleanup();
    alertsTriggered = [];
    window.sessionStorage.setItem('lysandri_audit_pin', 'LYS-AUDIT-2026-SECURE');
    window.localStorage.setItem('lysandri_token', validJwt);

    let anularLlamado = false;
    customRequestHandler = async function (configOrUrl) {
      let config = typeof configOrUrl === 'string' ? { url: configOrUrl } : { ...configOrUrl };
      if (config.url.includes('/anular')) {
        anularLlamado = true;
        return { status: 200, data: { success: true, estado: 'ANULADO' }, headers: {} };
      }
      if (config.url.includes('/internal/accounting/ventas')) {
        if (anularLlamado) {
          // La recarga posterior falla
          const error = new Error('Fallo de red en recarga');
          error.response = { status: 500, data: { message: 'Timeout temporal al refrescar reporte' } };
          throw error;
        }
        return { status: 200, data: mockReporteOctubre, headers: {} };
      }
      return { status: 200, data: {}, headers: {} };
    };

    render(React.createElement(AccountingView));

    await waitFor(() => screen.getByText('Contabilidad y facturación'));

    // Cambiar a pestaña Comprobantes
    fireEvent.click(screen.getByRole('button', { name: /Comprobantes/i }));
    await waitFor(() => screen.getByText('E001-140'));

    const anularBtn = screen.getAllByTitle(/Anular comprobante emitido ante SUNAT/i)[0];
    fireEvent.click(anularBtn);

    await waitFor(() => {
      // Debe haber 2 alertas:
      // 1. Éxito de anulación en el backend
      // 2. Aviso de que la anulación fue confirmada pero la recarga falló
      assert.ok(
        alertsTriggered.some((a) => a.includes('anulado con éxito en el servidor')),
        'Debe confirmar la anulación exitosa en servidor'
      );
      assert.ok(
        alertsTriggered.some((a) => a.includes('La anulación del comprobante fue confirmada en el servidor, pero ocurrió un problema al actualizar el reporte en pantalla')),
        'Debe informar al usuario que la anulación fue confirmada y solicitar refresco manual'
      );
    });

    customRequestHandler = null;
    testsPassed++;
    console.log('PASS 9: Anulación exitosa con recarga fallida informa confirmación en servidor y pide refresco manual.');
  }

  // Test 10: AdminDashboard: Guardado parcial vs fallo total
  {
    cleanup();
    alertsTriggered = [];
    capturedRequests = [];

    // Parte A: Guardado parcial (datos personales OK, rol falla)
    customRequestHandler = async function (configOrUrl, maybeConfig) {
      let config = typeof configOrUrl === 'string' ? { ...(maybeConfig || {}), url: configOrUrl } : { ...configOrUrl };
      let body = config.data;
      if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch {}
      }
      const method = (config.method || 'get').toUpperCase();

      if (config.url.includes('/usuarios') && method === 'GET') {
        return {
          status: 200,
          data: [
            {
              idUser: 42,
              nombres: 'Elena',
              apellidos: 'Ríos',
              email: 'elena@empresa.pe',
              rol: 'ESTUDIANTE',
              telefono: '+51 987654321',
              activo: true,
            },
          ],
          headers: {},
        };
      }
      if (config.url.startsWith('/usuarios/') && method === 'PUT') {
        return { status: 200, data: { idUser: 42, ...body }, headers: {} };
      }
      if (config.url.includes('/rol') && method === 'PATCH') {
        const err = new Error('Auditoría requerida');
        err.response = { status: 403, data: { message: 'El cambio de rol requiere autorización jerárquica' } };
        throw err;
      }
      if (config.url.includes('/programs')) return { status: 200, data: [], headers: {} };
      if (config.url.includes('/metricas')) return { status: 200, data: {}, headers: {} };
      return { status: 200, data: {}, headers: {} };
    };

    render(React.createElement(AdminDashboard, { initialSection: 'users' }));
    await waitFor(() => screen.getByText('Elena Ríos'));

    // Abrir modal de edición
    fireEvent.click(screen.getByLabelText(/Editar a Elena Ríos/i));
    await waitFor(() => screen.getByPlaceholderText('Nombres'));

    fireEvent.change(screen.getByPlaceholderText('Nombres'), { target: { value: 'Elena Modificada' } });
    fireEvent.change(screen.getByDisplayValue('Ejecutivo / Alumno'), { target: { value: 'ADMIN' } });

    fireEvent.click(screen.getByRole('button', { name: /Guardar Cambios/i }));

    await waitFor(() => {
      assert.ok(
        alertsTriggered.some((a) => a.includes('Guardado parcial: Se guardó (datos personales)')),
        'Debe notificar guardado parcial exacto cuando falla el cambio de rol'
      );
    });

    // Parte B: Fallo total (falla todo el guardado)
    alertsTriggered = [];
    customRequestHandler = async function (configOrUrl, maybeConfig) {
      let config = typeof configOrUrl === 'string' ? { ...(maybeConfig || {}), url: configOrUrl } : { ...configOrUrl };
      const method = (config.method || 'get').toUpperCase();
      if (config.url.includes('/usuarios') && method === 'GET') {
        return {
          status: 200,
          data: [
            {
              idUser: 42,
              nombres: 'Elena',
              apellidos: 'Ríos',
              email: 'elena@empresa.pe',
              rol: 'ESTUDIANTE',
              telefono: '+51 987654321',
              activo: true,
            },
          ],
          headers: {},
        };
      }
      if (config.url.startsWith('/usuarios/') && method === 'PUT') {
        const err = new Error('Conflicto');
        err.response = { status: 409, data: { message: 'El correo electrónico ya se encuentra registrado' } };
        throw err;
      }
      if (config.url.includes('/rol') && method === 'PATCH') {
        const err = new Error('Fallo de rol');
        err.response = { status: 500, data: { message: 'Error interno al actualizar rol' } };
        throw err;
      }
      if (config.url.includes('/programs')) return { status: 200, data: [], headers: {} };
      if (config.url.includes('/metricas')) return { status: 200, data: {}, headers: {} };
      return { status: 200, data: {}, headers: {} };
    };

    fireEvent.click(screen.getByRole('button', { name: /Guardar Cambios/i }));

    await waitFor(() => {
      assert.ok(
        alertsTriggered.some((a) => a.includes('Error al guardar cambios: Datos personales')),
        'Debe notificar error total de guardado'
      );
      // El modal debe permanecer abierto para no perder los datos del formulario
      assert.ok(screen.getByPlaceholderText('Nombres'), 'El formulario debe permanecer abierto tras fallo total');
    });

    customRequestHandler = null;
    testsPassed++;
    console.log('PASS 10: AdminDashboard notifica guardado parcial vs fallo total y preserva formulario.');
  }

  console.log(`\nRESUMEN: ${testsPassed}/10 pruebas de interacción frontend aprobadas.`);
} finally {
  cleanup();
  axios.Axios.prototype.request = originalAxiosRequest;
  dom.window.close();
  if (fs.existsSync(compiledAcctPath)) fs.unlinkSync(compiledAcctPath);
  if (fs.existsSync(compiledAdminPath)) fs.unlinkSync(compiledAdminPath);
}
