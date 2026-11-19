import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';
import { crearPuerta } from './puerta.js';
import { obtenerCloudflare } from './cloudflare.js';

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const estado = path.join(raiz, '.tunnel-session.json');
const direccion = 'http://127.0.0.1:3010';

async function main() {
  if (process.argv.includes('--stop')) {
    const datos = JSON.parse(await fs.readFile(estado, 'utf8'));
    const respuesta = await fetch(`${direccion}/__tunel/cerrar`, { method: 'POST', headers: { 'X-Lysandri-Control': datos.control }, signal: AbortSignal.timeout(5000) });
    if (!respuesta.ok) throw new Error('No se pudo cerrar el túnel de esta sesión.');
    console.log('Túnel cerrado. El proyecto local sigue funcionando.');
    return;
  }
  const comprobacion = await fetch('http://127.0.0.1:8080/api/v1/programas/informacion', { signal: AbortSignal.timeout(8000) }).catch(() => null);
  if (!comprobacion?.ok) throw new Error('El backend está apagado. Ejecuta npm run dev y espera PLATAFORMA DISPONIBLE.');
  console.log('Preparando la vista del proyecto para el enlace externo...');
  await new Promise((resolve, reject) => {
    const windows = process.platform === 'win32';
    const compilacion = spawn(windows ? (process.env.ComSpec || 'cmd.exe') : 'npm',
      windows ? ['/d', '/s', '/c', 'npm --prefix frontend run build'] : ['--prefix', 'frontend', 'run', 'build'],
      { cwd: raiz, windowsHide: true, stdio: 'inherit', env: { ...process.env, VITE_API_URL: '/api/v1' } });
    compilacion.once('error', reject);
    compilacion.once('exit', codigo => codigo === 0 ? resolve() : reject(new Error('No se pudo compilar el frontend. Revisa el error anterior.')));
  });
  const carpeta = path.join(raiz, 'frontend', 'dist');
  await fs.access(path.join(carpeta, 'index.html')).catch(() => { throw new Error('Primero ejecuta npm run build para preparar la vista de prueba.'); });
  const ejecutable = await obtenerCloudflare();
  const clave = randomBytes(12).toString('base64url');
  const control = randomBytes(32).toString('base64url');
  let tunel;
  let cerrando = false;
  let enlace;
  const cerrar = async (codigo = 0) => {
    if (cerrando) return;
    cerrando = true;
    tunel?.kill();
    servidor.closeAllConnections();
    servidor.close();
    try {
      const datos = JSON.parse(await fs.readFile(estado, 'utf8'));
      if (datos.control === control) await fs.unlink(estado);
    } catch { /* La sesión puede cerrarse antes de guardar su estado. */ }
    process.exitCode = codigo;
  };
  const servidor = crearPuerta({ carpeta, clave, control, cerrar });
  await new Promise((resolve, reject) => {
    servidor.once('error', reject);
    servidor.listen(3010, '127.0.0.1', resolve);
  });
  await fs.writeFile(estado, JSON.stringify({ pid: process.pid, control, direccion }), { mode: 0o600 });
  tunel = spawn(ejecutable, ['tunnel', '--no-autoupdate', '--protocol', 'http2', '--edge-ip-version', '4', '--url', direccion], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let salida = '';
  let anunciado = false;
  let conectado = false;
  const mostrar = () => {
    if (!enlace || anunciado) return;
    anunciado = true;
    console.log('\nENLACE DE PRUEBA LYSANDRI EXECUTIVE');
    console.log(`Plataforma:  ${enlace}`);
    console.log(`Intranet:    ${enlace}/intranet`);
    console.log(`Contabilidad: ${enlace}/contabilidad`);
    console.log(`Clave temporal: ${clave}`);
    console.log('\nPrimero ingresa esta clave y luego inicia sesión con tu cuenta de la plataforma.');
    console.log('Para cerrar: npm run tunnel -- --stop (o Ctrl+C en esta terminal).');
  };
  const recibir = async (datos) => {
    salida += datos.toString();
    const lineas = salida.split(/\r?\n/);
    salida = lineas.pop().slice(-12000);
    for (const linea of lineas) {
      const coincidencia = linea.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i);
      if (coincidencia && !enlace) {
        enlace = coincidencia[0];
        await fs.writeFile(estado, JSON.stringify({ pid: process.pid, control, direccion, enlace }), { mode: 0o600 });
      }
      if (linea.includes('Registered tunnel connection')) {
        if (anunciado && !conectado) console.log('Conexión con Cloudflare restablecida. El enlace y la clave siguen siendo los mismos.');
        conectado = true;
        mostrar();
      } else if (/Connection terminated|Retrying connection/.test(linea)) {
        if (conectado) console.log('Se interrumpió la conexión con Cloudflare. Reintentando automáticamente...');
        conectado = false;
      } else if (linea.includes('ERR') && !/client disconnected|context canceled/.test(linea)) {
        console.error(linea);
      }
    }
  };
  tunel.stdout.on('data', recibir);
  tunel.stderr.on('data', recibir);
  tunel.on('error', error => { console.error(error.message); cerrar(1); });
  tunel.on('exit', codigo => { if (!cerrando) { console.log(`Túnel finalizado (${codigo}).`); cerrar(codigo || 1); } });
  process.on('SIGINT', () => cerrar());
  process.on('SIGTERM', () => cerrar());
  console.log('Conectando Cloudflare con la vista protegida de prueba...');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
