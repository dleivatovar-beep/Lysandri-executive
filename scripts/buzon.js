import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export async function prepararBuzon() {
  const carpeta = path.join(os.tmpdir(), 'lysandri-correo');
  const ejecutable = path.join(carpeta, 'mailpit.exe');
  await fs.mkdir(carpeta, { recursive: true });
  try {
    const datos = JSON.parse(await fs.readFile(path.join(carpeta, 'version.json'), 'utf8'));
    if (createHash('sha256').update(await fs.readFile(ejecutable)).digest('hex') === datos.sha256) return ejecutable;
  } catch { /* Primera instalación del buzón local. */ }
  console.log('Descargando Mailpit desde axllent/mailpit...');
  const respuesta = await fetch('https://api.github.com/repos/axllent/mailpit/releases/latest', { headers: { 'User-Agent': 'Lysandri-Executive' }, signal: AbortSignal.timeout(30000) });
  if (!respuesta.ok) throw new Error('No se pudo consultar la versión oficial de Mailpit.');
  const version = await respuesta.json();
  const recurso = version.assets.find(a => a.name === 'mailpit-windows-amd64.zip');
  if (!recurso?.digest?.startsWith('sha256:')) throw new Error('La descarga oficial no incluye un SHA-256 verificable.');
  const descarga = await fetch(recurso.browser_download_url, { signal: AbortSignal.timeout(180000) });
  if (!descarga.ok) throw new Error('No se pudo descargar Mailpit.');
  const contenido = Buffer.from(await descarga.arrayBuffer());
  if ('sha256:'+createHash('sha256').update(contenido).digest('hex') !== recurso.digest) throw new Error('El archivo descargado no coincide con el SHA-256 oficial.');
  const zip = path.join(carpeta, 'mailpit.zip');
  await fs.writeFile(zip, contenido);
  const citar = valor => "'" + valor.replaceAll("'", "''") + "'";
  const comando = `Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = [IO.Compression.ZipFile]::OpenRead(${citar(zip)}); try { $entrada = $zip.GetEntry('mailpit.exe'); if (-not $entrada) { throw 'El archivo no contiene mailpit.exe' }; [IO.Compression.ZipFileExtensions]::ExtractToFile($entrada, ${citar(ejecutable)}, $true) } finally { $zip.Dispose() }`;
  const extraccion = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', comando], { windowsHide: true, encoding: 'utf8' });
  if (extraccion.status !== 0) throw new Error('No se pudo extraer el buzón local: ' + (extraccion.error?.message || extraccion.stderr || 'PowerShell no respondió.'));
  await fs.writeFile(path.join(carpeta, 'version.json'), JSON.stringify({ version: version.tag_name, sha256: createHash('sha256').update(await fs.readFile(ejecutable)).digest('hex') }));
  await fs.unlink(zip);
  return ejecutable;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  prepararBuzon().then(ejecutable => {
    console.log('Buzón local: http://127.0.0.1:8025 — SMTP: 127.0.0.1:1025');
    console.log('Los correos de prueba se consultan aquí; no se envían a Internet. Ctrl+C para cerrar.');
    const buzon = spawn(ejecutable, ['--listen', '127.0.0.1:8025', '--smtp', '127.0.0.1:1025'], {
      windowsHide: true, stdio: 'inherit', env: { ...process.env, MP_SMTP_RELAY_ALL: 'false', MP_SMTP_RELAY_CONFIG: '', MP_SMTP_FORWARD_CONFIG: '' },
    });
    buzon.on('error', error => { console.error(error.message); process.exitCode = 1; });
    buzon.on('exit', codigo => { process.exitCode = codigo || 0; });
    process.on('SIGINT', () => buzon.kill());
    process.on('SIGTERM', () => buzon.kill());
  }).catch(error => { console.error(error.message); process.exitCode = 1; });
}
