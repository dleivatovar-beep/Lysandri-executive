import { createHash } from 'node:crypto';
import { appendFile, mkdir, readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as esperar } from 'node:timers/promises';
import { parseArgs } from 'node:util';

const intervalo = 30_000;
const zona = 'America/Bogota';
const calendario = new Intl.DateTimeFormat('sv-SE', { timeZone: zona, year: 'numeric', month: '2-digit' });
const objetivo = 99.9;
const { positionals, values } = parseArgs({
  allowPositionals: true,
  options: {
    base: { type: 'string', default: 'http://127.0.0.1:3000' },
    carpeta: { type: 'string' }, mes: { type: 'string' },
    usuarios: { type: 'string', default: '12' },
    segundos: { type: 'string', default: '30' },
    pausa: { type: 'string', default: '1000' },
    'permitir-externo': { type: 'boolean' },
  },
});

const publico = [
  { nombre: 'Portada', ruta: '/', validar: (_, texto) => /id=["']root["']/.test(texto) },
  { nombre: 'Catálogo', ruta: '/api/v1/programas/informacion', validar: Array.isArray },
  { nombre: 'Medios de pago', ruta: '/api/v1/orders/metodos-pago', validar: objeto },
];
const internos = [
  { nombre: 'Beneficios', ruta: '/api/v1/pruebas/comerciales/beneficios', validar: Array.isArray, requiereSesion: true },
  { nombre: 'Conciliación', ruta: '/api/v1/pruebas/demostracion/pasarela', validar: Array.isArray, requiereSesion: true },
  { nombre: 'Órdenes corporativas', ruta: '/api/v1/pruebas/demostracion/empresas', validar: Array.isArray, requiereSesion: true },
];

function objeto(valor) {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function entero(valor, minimo, maximo, nombre) {
  if (!/^\d+$/.test(valor) || !Number.isSafeInteger(Number(valor)) || Number(valor) < minimo || Number(valor) > maximo) {
    throw new Error(`${nombre} debe estar entre ${minimo} y ${maximo}.`);
  }
  return Number(valor);
}

function origen(valor) {
  const url = new URL(valor);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/' ||
      !(url.protocol === 'https:' || (local && url.protocol === 'http:'))) {
    throw new Error('Usa solo el origen HTTPS del despliegue o HTTP en localhost.');
  }
  return { url: url.origin, local };
}

async function consulta(destino, endpoint, token) {
  const inicio = performance.now();
  let estado = 0;
  let valida = false;
  try {
    const respuesta = await fetch(new URL(endpoint.ruta, destino), {
      redirect: 'manual', signal: AbortSignal.timeout(5000),
      headers: endpoint.requiereSesion && token ? { Authorization: 'Bearer ' + token } : undefined,
    });
    estado = respuesta.status;
    if (respuesta.ok) {
      const texto = await respuesta.text();
      if (endpoint.ruta === '/') valida = endpoint.validar(null, texto);
      else valida = endpoint.validar(JSON.parse(texto));
    } else {
      await respuesta.body?.cancel();
    }
  } catch {
    // El registro conserva el resultado y el tiempo, sin guardar cuerpos ni cabeceras.
  }
  return { nombre: endpoint.nombre, estado, valida, ms: Math.round(performance.now() - inicio) };
}

function mesDe(fecha) {
  return calendario.format(new Date(fecha));
}

function rango(mes) {
  if (!/^20\d{2}-(0[1-9]|1[0-2])$/.test(mes)) throw new Error('Usa un mes con el formato AAAA-MM.');
  const desde = Date.parse(mes + '-01T00:00:00-05:00');
  const [anio, numero] = mes.split('-').map(Number);
  const hasta = Date.UTC(anio, numero, 1, 5);
  return [desde, hasta];
}

function archivo(carpeta, mes) {
  return join(carpeta, mes + '.jsonl');
}

async function registrar(destino, carpeta) {
  const fecha = Date.now();
  const ranura = Math.floor(fecha / intervalo) * intervalo;
  const resultados = await Promise.all(publico.slice(0, 2).map(endpoint => consulta(destino, endpoint)));
  const muestra = {
    hora: new Date(fecha).toISOString(), ranura,
    disponible: resultados.every(r => r.valida), consultas: resultados,
  };
  await mkdir(carpeta, { recursive: true });
  await appendFile(archivo(carpeta, mesDe(ranura)), JSON.stringify(muestra) + '\n', 'utf8');
  console.log(JSON.stringify(muestra));
}

async function informe(destino, carpeta) {
  const mes = values.mes ?? mesDe(Date.now());
  const [desde, hasta] = rango(mes);
  let texto = '';
  try { texto = await readFile(archivo(carpeta, mes), 'utf8'); }
  catch (fallo) { if (fallo.code !== 'ENOENT') throw fallo; }
  const muestras = new Map();
  let lineasInvalidas = 0;
  for (const linea of texto.split('\n')) {
    if (!linea.trim()) continue;
    try {
      const m = JSON.parse(linea);
      if (!Number.isSafeInteger(m.ranura) || m.ranura < desde || m.ranura >= hasta ||
          m.ranura % intervalo !== 0 || typeof m.disponible !== 'boolean') {
        lineasInvalidas++;
        continue;
      }
      muestras.set(m.ranura, (muestras.get(m.ranura) ?? true) && m.disponible);
    } catch { lineasInvalidas++; }
  }
  const total = (hasta - desde) / intervalo;
  const correctas = [...muestras.values()].filter(Boolean).length;
  const fallidas = muestras.size - correctas;
  const desconocidas = total - muestras.size;
  const terminado = Date.now() >= hasta;
  const porcentaje = cantidad => 100 * cantidad / total;
  const toleradas = Math.floor(total / 1000);
  const resultado = fallidas > toleradas ? 'OBJETIVO INCUMPLIDO'
    : terminado && desconocidas === 0 ? 'OBJETIVO ALCANZADO' : 'MEDICION INCOMPLETA';
  console.log(JSON.stringify({
    destino, mes, zona, objetivo, resultado, intervaloSegundos: intervalo / 1000,
    periodoTerminado: terminado, total, correctas, fallidas, desconocidas, lineasInvalidas,
    minutosIndisponiblesPermitidos: (hasta - desde) / 60_000 / 1000,
    coberturaPorcentaje: porcentaje(muestras.size),
    disponibilidadObservadaPorcentaje: muestras.size ? 100 * correctas / muestras.size : null,
    limitesDisponibilidadMensual: [porcentaje(correctas), porcentaje(correctas + desconocidas)],
  }, null, 2));
}

function percentil(datos, proporcion) {
  if (!datos.length) return null;
  return datos[Math.ceil(datos.length * proporcion) - 1];
}

async function carga(destino, local) {
  if (!local && !values['permitir-externo']) throw new Error('Usa --permitir-externo solo si el responsable del despliegue autorizó la carga.');
  const usuarios = entero(values.usuarios, 1, 100, 'Usuarios simultáneos');
  const segundos = entero(values.segundos, 5, 600, 'Duración');
  const pausa = entero(values.pausa, 100, 10_000, 'Pausa entre consultas');
  const token = process.env.LYSANDRI_TOKEN_MEDICION;
  const perfil = token ? [...publico, ...internos] : publico;
  const medidas = new Map(perfil.map(e => [e.nombre, []]));
  const inicio = performance.now();
  const fin = inicio + segundos * 1000;
  await Promise.all(Array.from({ length: usuarios }, async (_, indice) => {
    await esperar(indice * 20);
    let siguiente = indice % perfil.length;
    while (performance.now() < fin) {
      const endpoint = perfil[siguiente++ % perfil.length];
      const medicion = await consulta(destino, endpoint, token);
      medidas.get(endpoint.nombre).push(medicion);
      await esperar(Math.min(pausa, Math.max(0, fin - performance.now())));
    }
  }));
  const filas = [...medidas.entries()].map(([endpoint, registros]) => {
    const tiempos = registros.filter(r => r.valida).map(r => r.ms).sort((a, b) => a - b);
    return {
      endpoint, consultas: registros.length, errores: registros.length - tiempos.length,
      fueraDeDosSegundos: tiempos.filter(t => t >= 2000).length,
      p50Ms: percentil(tiempos, 0.50), p95Ms: percentil(tiempos, 0.95), maxMs: tiempos.at(-1) ?? null,
    };
  });
  console.log(JSON.stringify({
    destino, hora: new Date().toISOString(), usuarios, segundos, pausaMs: pausa, endpointsInternos: Boolean(token),
    consultas: filas.reduce((total, fila) => total + fila.consultas, 0), endpoints: filas,
  }, null, 2));
  if (filas.some(fila => !fila.consultas || fila.errores || fila.fueraDeDosSegundos)) process.exitCode = 1;
}

async function main() {
  const [modo] = positionals;
  if (positionals.length !== 1 || !['monitor', 'muestra', 'informe', 'carga'].includes(modo)) {
    throw new Error('Uso: npm run medicion -- monitor|muestra|informe|carga [--base URL] [--mes AAAA-MM] [--usuarios N] [--segundos N]');
  }
  const destino = origen(values.base);
  const id = createHash('sha256').update(destino.url).digest('hex').slice(0, 16);
  const carpeta = join(values.carpeta ?? join(homedir(), 'Lysandri medicion'), id);
  if (modo === 'muestra') await registrar(destino.url, carpeta);
  if (modo === 'informe') await informe(destino.url, carpeta);
  if (modo === 'carga') await carga(destino.url, destino.local);
  if (modo === 'monitor') {
    let detener = false;
    const control = new AbortController();
    const parar = () => { detener = true; control.abort(); };
    process.once('SIGINT', parar);
    process.once('SIGTERM', parar);
    try {
      while (!detener) {
        await registrar(destino.url, carpeta);
        const proxima = (Math.floor(Date.now() / intervalo) + 1) * intervalo;
        await esperar(Math.max(0, proxima - Date.now()), undefined, { signal: control.signal });
      }
    } catch (fallo) { if (!detener || fallo.name !== 'AbortError') throw fallo; }
  }
}

main().catch(fallo => {
  console.error(fallo.message);
  process.exitCode = 1;
});
