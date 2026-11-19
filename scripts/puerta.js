import http from 'node:http';
import path from 'node:path';
import fs from 'node:fs';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

const tipos = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.svg': 'image/svg+xml', '.gif': 'image/gif', '.webp': 'image/webp', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf' };
const formulario = `<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Prueba de Lysandri Executive</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#091421;color:#eef7fc;font:16px system-ui}form{width:min(350px,80vw);padding:32px;border:1px solid #294357;border-radius:12px;background:#102234}h1{font-size:24px}p{color:#bdcdd8;line-height:1.5}input,button{box-sizing:border-box;width:100%;padding:13px;margin-top:12px;border-radius:6px;border:1px solid #38556b;font:inherit}input{background:#091421;color:white}button{background:#49c9e8;color:#071827;font-weight:700;cursor:pointer}</style><form method="post" action="/acceso" onsubmit="if(location.pathname!='/acceso')document.getElementById('volver').value=location.pathname+location.search+location.hash"><input type="hidden" id="volver" name="volver" value="__RETORNO__"><h1>Lysandri Executive</h1><p>Ingresa la clave que te compartieron para acceder a la prueba.</p><label for="clave">Clave de acceso</label><input id="clave" name="clave" type="password" required autocomplete="off"><button>Entrar a la prueba</button></form></html>`;
const hash = (valor) => createHash('sha256').update(valor).digest();
function retornoSeguro(valor) {
  try {
    if (!valor || !valor.startsWith('/') || valor.startsWith('//') || /[\\\r\n]/.test(valor)) return '/';
    const url = new URL(valor, 'https://prueba.local');
    return url.origin === 'https://prueba.local' ? url.pathname + url.search + url.hash : '/';
  } catch { return '/'; }
}
const pantalla = (retorno = '/') => formulario.replace('__RETORNO__', retorno.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;'));


export function crearPuerta({ carpeta, backend = 'http://127.0.0.1:8080', clave, control, cerrar = () => {} }) {
  const sesion = randomBytes(32).toString('base64url');
  const raiz = fs.realpathSync(carpeta);
  const servidor = http.createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    let ruta;
    try { ruta = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).split('/').map(p => p.split(';')[0]).join('/').replace(/\/{2,}/g, '/'); }
    catch { res.writeHead(400).end(); return; }
    if (ruta.startsWith('/__tunel/')) {
      if (req.headers['x-lysandri-control'] !== control || req.headers['cf-ray']) { res.writeHead(403).end(); return; }
      if (ruta === '/__tunel/cerrar' && req.method === 'POST') { res.end('Cerrando'); setImmediate(cerrar); return; }
      res.writeHead(404).end(); return;
    }
    if (ruta === '/acceso' && req.method === 'POST') {
      try {
        let cuerpo = '';
        for await (const parte of req) { cuerpo += parte; if (cuerpo.length > 4096) throw new Error('Solicitud demasiado grande'); }
        const datos = new URLSearchParams(cuerpo);
        const retorno = retornoSeguro(datos.get('volver'));
        if (!timingSafeEqual(hash(datos.get('clave') || ''), hash(clave))) {
          res.writeHead(401, { 'Content-Type': 'text/html; charset=utf-8' }).end(pantalla(retorno)); return;
        }
        res.writeHead(303, { Location: retorno, 'Set-Cookie': `lysandri_prueba=${sesion}; Path=/; HttpOnly; Secure; SameSite=Lax` }).end();
      } catch { res.writeHead(400).end(); }
      return;
    }
    const cookies = (req.headers.cookie || '').split(';').map(c => c.trim());
    if (!cookies.includes(`lysandri_prueba=${sesion}`)) {
      if (ruta.startsWith('/api/') || ruta.startsWith('/internal/')) {
        res.writeHead(401, { 'Content-Type': 'application/json' }).end(JSON.stringify({ mensaje: 'Ingresa primero la clave de la prueba.' }));
      } else { res.writeHead(401, { 'Content-Type': 'text/html; charset=utf-8' }).end(pantalla()); }
      return;
    }
    if (/^\/api\/(?:v1\/)?auth\/register-interno(?:\/|$)/.test(ruta)) {
      res.writeHead(403, { 'Content-Type': 'application/json' }).end(JSON.stringify({ mensaje: 'El registro de personal se realiza desde el acceso local.' })); return;
    }
    if (ruta.startsWith('/api/') || ruta.startsWith('/internal/')) {
      const destino = new URL(backend);
      const headers = { ...req.headers, host: destino.host };
      delete headers['x-lysandri-control'];
      if (headers.origin) {
        try {
          if (new URL(headers.origin).host !== req.headers.host) { res.writeHead(403).end(); return; }
        } catch { res.writeHead(403).end(); return; }
        delete headers.origin;
      }
      headers.cookie = cookies.filter(c => !c.startsWith('lysandri_prueba=')).join('; ');
      const llamada = http.request({ hostname: destino.hostname, port: destino.port, path: req.url, method: req.method, headers }, respuesta => {
        res.writeHead(respuesta.statusCode, respuesta.headers); respuesta.pipe(res);
      });
      llamada.setTimeout(180000, () => llamada.destroy(new Error('Tiempo de espera agotado')));
      llamada.on('error', () => {
        if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' }).end(JSON.stringify({ mensaje: 'El backend no está disponible. Inicia npm run dev en el equipo.' }));
        else res.destroy();
      });
      req.on('aborted', () => llamada.destroy());
      res.on('close', () => { if (!res.writableEnded) llamada.destroy(); });
      req.pipe(llamada); return;
    }
    if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
    if (ruta.includes('\0') || ruta.includes('\\') || /\/(?:\.|@|src(?:\/|$)|node_modules(?:\/|$)|__)/.test(ruta) || ruta.endsWith('.map')) {
      res.writeHead(404).end(); return;
    }
    let archivo = path.resolve(raiz, '.' + ruta);
    if (!archivo.startsWith(raiz + path.sep) && archivo !== raiz) { res.writeHead(404).end(); return; }
    if (!fs.existsSync(archivo) || fs.statSync(archivo).isDirectory()) {
      if (path.extname(ruta)) { res.writeHead(404).end(); return; }
      archivo = path.join(raiz, 'index.html');
    }
    if (!fs.realpathSync(archivo).startsWith(raiz + path.sep)) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': tipos[path.extname(archivo)] || 'application/octet-stream' });
    if (req.method === 'HEAD') res.end(); else fs.createReadStream(archivo).pipe(res);
  });
  return servidor;
}
