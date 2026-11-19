import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { crearPuerta } from './puerta.js';

const carpeta = await fs.mkdtemp(path.join(os.tmpdir(), 'lysandri-tunnel-test-'));
await fs.writeFile(path.join(carpeta, 'index.html'), '<html>Lysandri Executive</html>');
const backend = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ ruta: req.url, autorizacion: req.headers.authorization, origen: req.headers.origin, cookie: req.headers.cookie }));
});
await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve));
let cerrado = false;
const puerta = crearPuerta({ carpeta, backend: `http://127.0.0.1:${backend.address().port}`, clave: 'clave-de-la-prueba', control: 'control-local', cerrar() { cerrado = true; } });
await new Promise(resolve => puerta.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${puerta.address().port}`;
try {
  assert.equal((await fetch(url)).status, 401);
  assert.equal((await fetch(url+'/acceso', { method: 'POST', body: 'clave=incorrecta' })).status, 401);
  const acceso = await fetch(url+'/acceso', { method: 'POST', body: 'clave=clave-de-la-prueba', redirect: 'manual' });
  assert.equal(acceso.status, 303);
  assert.match(acceso.headers.get('set-cookie'), /HttpOnly; Secure; SameSite=Lax/);
  const vuelta = '/intranet#recuperar=' + 'a'.repeat(43);
  const recuperacion = await fetch(url+'/acceso', { method: 'POST', body: new URLSearchParams({clave:'clave-de-la-prueba',volver:vuelta}), redirect: 'manual' });
  assert.equal(recuperacion.headers.get('location'), vuelta);
  const externa = await fetch(url+'/acceso', { method: 'POST', body: new URLSearchParams({clave:'clave-de-la-prueba',volver:'//otro-sitio.invalid'}), redirect: 'manual' });
  assert.equal(externa.headers.get('location'), '/');
  const cookie = acceso.headers.get('set-cookie').split(';')[0];
  const headers = { cookie };
  assert.match(await (await fetch(url, { headers })).text(), /Lysandri Executive/);
  assert.equal((await fetch(url+'/contabilidad', { headers })).status, 200);
  for (const ruta of ['/.env', '/@fs/archivo', '/src/main.tsx', '/node_modules/react', '/assets/missing.js', '/assets/test.map']) {
    assert.equal((await fetch(url+ruta, { headers })).status, 404, ruta);
  }
  for (const ruta of ['/api/v1/auth/register-interno', '/api/v1/auth/register-interno/', '/api/v1/auth;param=1/register-interno']) {
    assert.equal((await fetch(url+ruta, { method: 'POST', headers })).status, 403, ruta);
  }
  const api = await fetch(url+'/api/v1/programas/informacion', { headers: { ...headers, authorization: 'Bearer token-de-prueba', origin: url } });
  const datos = await api.json();
  assert.equal(datos.autorizacion, 'Bearer token-de-prueba');
  assert.equal(datos.origen, undefined);
  assert.equal(datos.cookie, '');
  assert.equal((await fetch(url+'/api/v1/auth/login', { headers: { ...headers, origin: 'https://otro-sitio.invalid' } })).status, 403);
  assert.equal((await fetch(url+'/__tunel/cerrar', { method: 'POST', headers })).status, 403);
  assert.equal((await fetch(url+'/__tunel/cerrar', { method: 'POST', headers: { 'X-Lysandri-Control': 'control-local', 'CF-Ray': 'externo' } })).status, 403);
  assert.equal((await fetch(url+'/__tunel/cerrar', { method: 'POST', headers: { 'X-Lysandri-Control': 'control-local' } })).status, 200);
  assert.equal(cerrado, true);
  console.log('PASS: clave temporal, rutas de la aplicación, JWT, origen, bloqueo de registro interno y archivos privados, cierre autorizado.');
} finally {
  puerta.closeAllConnections(); backend.closeAllConnections();
  await Promise.all([new Promise(resolve => puerta.close(resolve)), new Promise(resolve => backend.close(resolve))]);
  await fs.unlink(path.join(carpeta, 'index.html'));
  await fs.rmdir(carpeta);
}
