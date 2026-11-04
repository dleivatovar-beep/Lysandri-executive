/**
 * test-arranque-real.mjs
 * Prueba de validación de arranque y cierre real del coordinador:
 * 1. Ejecuta 'node frontend/scripts/iniciar-entorno.js --no-abrir-navegador'
 * 2. Verifica la aparición de 'PLATAFORMA DISPONIBLE'
 * 3. Comprueba http://127.0.0.1:3000/
 * 4. Comprueba http://127.0.0.1:3000/__lysandri_dev_id
 * 5. Comprueba el catálogo oficial mediante el proxy de Vite en http://127.0.0.1:3000/api/v1/orders/cuentas-bancarias
 * 6. Comprueba http://127.0.0.1:3000/api/v1/programas
 * 7. Envía señal de terminación (SIGINT)
 * 8. Confirma que el cierre termina los procesos propios y libera el bloqueo
 * 9. Confirma que los servicios preexistentes (backend 8080, postgres 5434) permanecen activos.
 */

import http from 'http';
import net from 'net';
import { spawn } from 'child_process';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const LOCK_FILE = path.resolve(__dirname, '.dev-orchestrator.lock');

function httpGet(url, timeoutMs = 3000) {
  return new Promise((resolve) => {
    const req = http.get(url, { timeout: timeoutMs }, (res) => {
      let body = '';
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        resolve({ ok: res.statusCode >= 200 && res.statusCode < 300, statusCode: res.statusCode, body });
      });
    });
    req.on('error', (err) => resolve({ ok: false, error: err.message, body: '' }));
    req.on('timeout', () => {
      req.destroy();
      resolve({ ok: false, error: 'TIMEOUT', body: '' });
    });
  });
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function run() {
  console.log('=== INICIANDO VALIDACIÓN REAL DEL COORDINADOR DE ENTORNO ===\n');

  // Evaluar estado previo del backend
  const initialBackend = await httpGet('http://127.0.0.1:8080/api/v1/orders/cuentas-bancarias', 1000);
  const backendEstabaEncendido = initialBackend.ok;
  console.log(`[TEST] Estado inicial del backend en 8080: ${backendEstabaEncendido ? 'ACTIVO (preexistente)' : 'APAGADO (inicio en frío requerido)'}`);

  let stdoutData = '';
  let stderrData = '';
  let plataformaDisponibleDetectada = false;

  const coordinatorProc = spawn(process.execPath, [
    path.resolve(__dirname, 'scripts', 'iniciar-entorno.js'),
    '--no-abrir-navegador',
  ], {
    cwd: path.resolve(__dirname),
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: true,
  });

  coordinatorProc.stdout.on('data', (chunk) => {
    const text = chunk.toString();
    stdoutData += text;
    process.stdout.write(text);
    if (text.includes('PLATAFORMA DISPONIBLE')) {
      plataformaDisponibleDetectada = true;
    }
  });

  coordinatorProc.stderr.on('data', (chunk) => {
    const text = chunk.toString();
    stderrData += text;
    process.stderr.write(text);
  });

  console.log(`[TEST] Coordinador lanzado con PID ${coordinatorProc.pid}. Esperando disponibilidad...`);

  // Esperar a que el coordinador esté listo (máximo 90 segundos para arranque en frío con Java)
  const timeoutMs = backendEstabaEncendido ? 45000 : 90000;
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (plataformaDisponibleDetectada) break;
    await sleep(250);
  }

  if (!plataformaDisponibleDetectada) {
    console.error('\n[FATAL] Timeout esperando PLATAFORMA DISPONIBLE en el coordinador.');
    coordinatorProc.kill('SIGINT');
    process.exit(1);
  }

  console.log('\n[TEST] 1. Cartel PLATAFORMA DISPONIBLE confirmado.');

  // Comprobar bloqueo activo en disco
  const lockExisteActivo = fs.existsSync(LOCK_FILE);
  console.log(`[TEST] 2. Archivo de bloqueo ${LOCK_FILE} presente en disco: ${lockExisteActivo}`);

  // Comprobar Vite 3000 HTML
  const resHtml = await httpGet('http://127.0.0.1:3000/');
  console.log(`[TEST] 3. GET http://127.0.0.1:3000/ -> Status ${resHtml.statusCode}, HTML presente: ${resHtml.body.includes('<div id="root">')}`);

  // Comprobar endpoint de identidad
  const resDevId = await httpGet('http://127.0.0.1:3000/__lysandri_dev_id');
  let devIdData = null;
  try { devIdData = JSON.parse(resDevId.body); } catch (_) {}
  console.log(`[TEST] 4. GET http://127.0.0.1:3000/__lysandri_dev_id -> app: '${devIdData?.app}', role: '${devIdData?.role}', workspaceSignature: '${devIdData?.workspaceSignature}'`);

  // Comprobar catálogo oficial vía proxy de Vite
  const resProxyCuentas = await httpGet('http://127.0.0.1:3000/api/v1/orders/cuentas-bancarias');
  console.log(`[TEST] 5. Proxy Vite -> Backend: GET http://127.0.0.1:3000/api/v1/orders/cuentas-bancarias -> Status ${resProxyCuentas.statusCode}`);

  const resProxyProgramas = await httpGet('http://127.0.0.1:3000/api/v1/programas');
  console.log(`[TEST] 6. Proxy Vite -> Backend Catálogo: GET http://127.0.0.1:3000/api/v1/programas -> Status ${resProxyProgramas.statusCode} (Sin ECONNREFUSED)`);

  // Probar cierre ordenado enviando señal SIGINT vía stdin (y kill si no responde)
  console.log('\n[TEST] Enviando señal de terminación limpia al coordinador...');
  coordinatorProc.stdin.write('SIGINT\n');

  const waitExit = Date.now();
  let exitedCode = null;
  await new Promise((resolve) => {
    coordinatorProc.on('exit', (code) => {
      exitedCode = code;
      resolve();
    });
    // Fallback de seguridad si en 10s no terminó
    setTimeout(() => {
      try { coordinatorProc.kill('SIGINT'); } catch (_) {}
      resolve();
    }, 10000);
  });
  console.log(`[TEST] Coordinador finalizó con código: ${exitedCode}`);

  // Esperar 1000ms tras salida para que el SO recoja sockets
  await sleep(1000);

  // Verificar que el bloqueo fue liberado
  const lockLiberado = !fs.existsSync(LOCK_FILE);
  console.log(`[TEST] 7. Archivo de bloqueo liberado de disco: ${lockLiberado}`);

  // Verificar que Vite puerto 3000 ya no está respondiendo
  const resVitePost = await httpGet('http://127.0.0.1:3000/', 1000);
  console.log(`[TEST] 8. Vite en puerto 3000 terminado: ${!resVitePost.ok}`);

  // Verificar estado del backend tras el cierre
  const resBackendPost = await httpGet('http://127.0.0.1:8080/api/v1/orders/cuentas-bancarias', 2000);
  let backendEstadoCorrecto = false;
  if (backendEstabaEncendido) {
    backendEstadoCorrecto = resBackendPost.ok;
    console.log(`[TEST] 9. Backend preexistente en puerto 8080 preservado y respondiendo: ${backendEstadoCorrecto}`);
  } else {
    backendEstadoCorrecto = !resBackendPost.ok;
    console.log(`[TEST] 9. Backend iniciado por coordinador terminado limpiamente en puerto 8080: ${backendEstadoCorrecto}`);
  }

  // Verificar que el clon de Postgres sigue vivo e intacto
  const pgSocket = new net.Socket();
  const pgVivo = await new Promise((resolve) => {
    pgSocket.setTimeout(1500);
    pgSocket.on('connect', () => { pgSocket.destroy(); resolve(true); });
    pgSocket.on('error', () => resolve(false));
    pgSocket.on('timeout', () => { pgSocket.destroy(); resolve(false); });
    pgSocket.connect(5434, '127.0.0.1');
  });
  console.log(`[TEST] 10. PostgreSQL clon persistente en 127.0.0.1:5434 preservado e intacto: ${pgVivo}`);

  const exitoTotal =
    plataformaDisponibleDetectada &&
    lockExisteActivo &&
    resHtml.ok &&
    devIdData?.app === 'lysandri-executive' &&
    resProxyCuentas.ok &&
    resProxyProgramas.ok &&
    lockLiberado &&
    !resVitePost.ok &&
    backendEstadoCorrecto &&
    pgVivo;

  console.log('\n================================================================================');
  console.log(`RESULTADO VALIDACIÓN DE ARRANQUE Y CIERRE: ${exitoTotal ? 'ÉXITO TOTAL' : 'FALLO'}`);
  console.log('================================================================================');

  process.exit(exitoTotal ? 0 : 1);
}

run().catch((err) => {
  console.error('[TEST FATAL]', err);
  process.exit(1);
});
