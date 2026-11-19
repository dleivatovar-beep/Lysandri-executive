import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';

export async function obtenerCloudflare() {
  const carpeta = path.join(os.tmpdir(), 'lysandri-tunel');
  await fs.mkdir(carpeta, { recursive: true });
  const archivo = path.join(carpeta, 'cloudflared.exe');
  const registro = path.join(carpeta, 'version.json');
  try {
    const datos = JSON.parse(await fs.readFile(registro, 'utf8'));
    if (createHash('sha256').update(await fs.readFile(archivo)).digest('hex') === datos.sha256) return archivo;
  } catch { /* La primera ejecución descarga la herramienta oficial. */ }
  if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('Esta instalación está preparada para Windows de 64 bits.');
  console.log('Descargando cloudflared de cloudflare/cloudflared...');
  const respuesta = await fetch('https://api.github.com/repos/cloudflare/cloudflared/releases/latest', { headers: { 'User-Agent': 'Lysandri-Executive' }, signal: AbortSignal.timeout(30000) });
  if (!respuesta.ok) throw new Error(`No se pudo consultar la distribución oficial (${respuesta.status}).`);
  const version = await respuesta.json();
  const recurso = version.assets.find(a => a.name === 'cloudflared-windows-amd64.exe');
  if (!recurso?.digest?.startsWith('sha256:')) throw new Error('La distribución no incluye un SHA-256 verificable.');
  const descarga = await fetch(recurso.browser_download_url, { signal: AbortSignal.timeout(180000) });
  if (!descarga.ok) throw new Error(`No se pudo descargar cloudflared (${descarga.status}).`);
  const contenido = Buffer.from(await descarga.arrayBuffer());
  const sha256 = createHash('sha256').update(contenido).digest('hex');
  if (`sha256:${sha256}` !== recurso.digest) throw new Error('El SHA-256 no coincide; no se ejecutó el archivo.');
  await fs.writeFile(archivo, contenido);
  await fs.writeFile(registro, JSON.stringify({ version: version.tag_name, sha256 }));
  return archivo;
}
