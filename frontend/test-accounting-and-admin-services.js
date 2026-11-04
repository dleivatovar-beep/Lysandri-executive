import assert from 'node:assert/strict';
import path from 'node:path';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';

// Mock storage
const sessionMap = new Map();
const localMap = new Map();
globalThis.window = {
  location: { href: '' },
  dispatchEvent: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
  sessionStorage: {
    getItem: (k) => sessionMap.get(k) || null,
    setItem: (k, v) => sessionMap.set(k, String(v)),
    removeItem: (k) => sessionMap.delete(k),
    clear: () => sessionMap.clear(),
  },
  localStorage: {
    getItem: (k) => localMap.get(k) || null,
    setItem: (k, v) => localMap.set(k, String(v)),
    removeItem: (k) => localMap.delete(k),
    clear: () => localMap.clear(),
  },
};
globalThis.sessionStorage = globalThis.window.sessionStorage;
globalThis.localStorage = globalThis.window.localStorage;

// Compilar con esbuild para resolver imports de typescript
const entry = path.resolve(import.meta.dirname, 'src/services/index.ts');
const tmpEntry = path.resolve(import.meta.dirname, '.tmp-test-services-entry.ts');
fs.writeFileSync(tmpEntry, `
export { apiClient } from './src/services/api';
export { accountingService } from './src/services/accountingService';
export { academicService } from './src/services/academicService';
`);

const compiledPath = path.resolve(import.meta.dirname, '.test-compiled-services.mjs');
esbuild.buildSync({
  entryPoints: [tmpEntry],
  bundle: true,
  format: 'esm',
  platform: 'node',
  outfile: compiledPath,
  external: ['axios', 'lucide-react', 'react', 'react-dom', 'react-router-dom'],
});
fs.unlinkSync(tmpEntry);

const { apiClient, accountingService, academicService } = await import(pathToFileURL(compiledPath).href);

console.log('================================================================================');
console.log('PRUEBAS UNITARIAS: Servicios de Contabilidad, Programas y Usuarios');
console.log('================================================================================');

let recordedRequests = [];

// Interceptar peticiones de apiClient
apiClient.interceptors.request.use((config) => {
  let reqData = config.data;
  if (typeof reqData === 'string') {
    try {
      reqData = JSON.parse(reqData);
    } catch {}
  }
  recordedRequests.push({
    method: config.method,
    url: config.url,
    headers: config.headers,
    params: config.params,
    data: reqData,
  });
  return config;
});

// Mock adapter para responder
apiClient.defaults.adapter = async (config) => {
  if (config.url === '/internal/accounting/ventas') {
    if (config.headers['X-Audit-PIN'] === 'BAD-PIN') {
      const err = new Error('Acceso no autorizado');
      err.response = { status: 403, data: { message: 'PIN incorrecto' } };
      throw err;
    }
    return {
      status: 200,
      data: {
        totalVentasPen: 1000,
        totalVentasUsd: 200,
        periodoSeleccionado: config.params?.periodo || 'TODOS',
        ventasConsolidadas: [],
      },
    };
  }

  if (config.url.startsWith('/internal/accounting/comprobantes/') && config.url.endsWith('/anular')) {
    if (config.url.includes('/9999/anular')) {
      const err = new Error('No encontrado');
      err.response = { status: 404, data: { message: 'Comprobante no existe' } };
      throw err;
    }
    return {
      status: 200,
      data: { mensaje: 'Comprobante anulado' },
    };
  }

  if (config.url === '/internal/accounting/ventas/export-sire') {
    return {
      status: 200,
      data: '20609812451|PRUEBA|SIRE|\n',
    };
  }

  if (config.url.startsWith('/usuarios/') && config.method === 'put') {
    // Verificar que no se envíen rol ni activo en PUT /usuarios/{id}
    const dataObj = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    if (dataObj && (dataObj.rol !== undefined || dataObj.activo !== undefined)) {
      const err = new Error('Access denied');
      err.response = { status: 403, data: { message: 'PUT /usuarios/{id} no permite rol ni activo' } };
      throw err;
    }
    return {
      status: 200,
      data: { idUser: 42, ...dataObj },
    };
  }

  if (config.url.startsWith('/usuarios/') && config.url.includes('/rol')) {
    return {
      status: 200,
      data: { idUser: 42, rol: config.params?.rol },
    };
  }

  if (config.url.startsWith('/usuarios/') && config.url.includes('/desactivar')) {
    return {
      status: 200,
      data: { idUser: 42, activo: false },
    };
  }

  return { status: 200, data: {} };
};

// 1. Contabilidad: fetchReporteVentas
recordedRequests = [];
const rep = await accountingService.fetchReporteVentas('LYS-AUDIT-2026-SECURE', '2026-10');
assert.equal(rep.periodoSeleccionado, '2026-10');
assert.equal(recordedRequests[0].url, '/internal/accounting/ventas');
assert.equal(recordedRequests[0].headers['X-Audit-PIN'], 'LYS-AUDIT-2026-SECURE');
assert.equal(recordedRequests[0].params.periodo, '2026-10');
console.log('✅ TEST 1 APROBADO: fetchReporteVentas envía X-Audit-PIN y período YYYY-MM');

// 2. Contabilidad: fetchReporteVentas error handling
await assert.rejects(
  async () => {
    await accountingService.fetchReporteVentas('BAD-PIN');
  },
  (err) => {
    assert.equal(err.response?.status, 403);
    return true;
  }
);
console.log('✅ TEST 2 APROBADO: fetchReporteVentas propaga errores 403 de PIN/acceso sin datos ficticios');

// 3. Contabilidad: anularComprobante con idComprobante
recordedRequests = [];
const anularRes = await accountingService.anularComprobante(888, 'Error en RUC', 'LYS-AUDIT-2026-SECURE');
assert.equal(anularRes.mensaje, 'Comprobante anulado');
assert.equal(recordedRequests[0].url, '/internal/accounting/comprobantes/888/anular');
assert.equal(recordedRequests[0].data.motivo, 'Error en RUC');
console.log('✅ TEST 3 APROBADO: anularComprobante usa idComprobante real y propaga motivo');

// 4. Contabilidad: anularComprobante error handling
await assert.rejects(
  async () => {
    await accountingService.anularComprobante(9999, 'Motivo', 'PIN');
  },
  (err) => {
    assert.equal(err.response?.status, 404);
    return true;
  }
);
console.log('✅ TEST 4 APROBADO: anularComprobante propaga errores del servidor sin simular éxito');

// 5. Normalizador de Programas
const rawProg1 = {
  idPrograma: 10,
  tituloPrograma: 'Machine Learning Avanzado',
  nombreInstructor: 'Dr. John Doe',
  duracionPrograma: '60 horas',
  coverUrl: 'https://example.com/cover.jpg',
  precio: 0, // No debe convertirse en 5
  moneda: 'USD',
  moodleCourseId: 501,
};
const norm1 = academicService.normalizeProgram(rawProg1);
assert.equal(norm1.titulo, 'Machine Learning Avanzado');
assert.equal(norm1.instructor, 'Dr. John Doe');
assert.equal(norm1.precio, 0);
assert.equal(norm1.moneda, 'USD');
assert.equal(norm1.moodleCourseId, 501);

const rawProg2 = {
  id: 11,
  titulo: 'Finanzas Cuantitativas',
  instructor: 'Dra. Jane Smith',
  duracion: '40 horas',
  imagenPortadaUrl: 'https://example.com/cover2.jpg',
  precio: 1500,
  moneda: 'PEN',
};
const norm2 = academicService.normalizeProgram(rawProg2);
assert.equal(norm2.titulo, 'Finanzas Cuantitativas');
assert.equal(norm2.precio, 1500);
assert.equal(norm2.moneda, 'PEN');
console.log('✅ TEST 5 APROBADO: normalizeProgram unifica variantes de nombres y conserva precio/moneda reales');

// 6. Edición administrativa de usuario: PUT omite rol y activo
recordedRequests = [];
const updatedUser = await academicService.updateUser(42, {
  nombres: 'Danny',
  apellidos: 'Levano',
  email: 'danny@lysandri.com',
  telefono: '999888777',
  rol: 'ADMIN', // Debe ser omitido
  activo: true, // Debe ser omitido
});
assert.equal(updatedUser.nombres, 'Danny');
assert.equal(recordedRequests[0].data.rol, undefined);
assert.equal(recordedRequests[0].data.activo, undefined);
assert.equal(recordedRequests[0].data.nombres, 'Danny');
console.log('✅ TEST 6 APROBADO: updateUser excluye rol y activo en PUT /usuarios/{id}');

// 7. Modificación de rol administrativa vía PATCH
recordedRequests = [];
await academicService.updateUserRole(42, 'CONTABILIDAD', 'Reasignación de funciones');
assert.equal(recordedRequests[0].method, 'patch');
assert.equal(recordedRequests[0].url, '/usuarios/42/rol');
assert.equal(recordedRequests[0].params.rol, 'CONTABILIDAD');
assert.equal(recordedRequests[0].params.motivo, 'Reasignación de funciones');
console.log('✅ TEST 7 APROBADO: updateUserRole utiliza PATCH /usuarios/{id}/rol con motivo de auditoría');

// 8. Desactivación administrativa vía PATCH
recordedRequests = [];
await academicService.deactivateUser(42, 'Cese de actividades');
assert.equal(recordedRequests[0].method, 'patch');
assert.equal(recordedRequests[0].url, '/usuarios/42/desactivar');
assert.equal(recordedRequests[0].params.motivo, 'Cese de actividades');
console.log('✅ TEST 8 APROBADO: deactivateUser utiliza PATCH /usuarios/{id}/desactivar con motivo');

// Limpieza de artefacto temporal
try { fs.unlinkSync(compiledPath); } catch {}

console.log('================================================================================');
console.log('RESUMEN: 8 de 8 pruebas unitarias frontend aprobadas exitosamente.');
console.log('================================================================================');
