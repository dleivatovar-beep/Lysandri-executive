import { spawn } from 'child_process';

console.log('\x1b[36m%s\x1b[0m', '⚡ Iniciando túnel seguro de Cloudflare para Lysandri Executive...');
console.log('Conectando con http://localhost:3000 ...\n');

const isWin = process.platform === 'win32';
const npxCmd = isWin ? 'npx.cmd' : 'npx';

const tunnel = spawn(npxCmd, ['-y', 'cloudflared', 'tunnel', '--url', 'http://localhost:3000'], {
  stdio: ['inherit', 'pipe', 'pipe'],
  shell: true,
});

let urlFound = false;

function processOutput(data) {
  const text = data.toString();
  
  // Detect Cloudflare Tunnel URL
  const match = text.match(/https:\/\/[a-zA-Z0-9-]+\.trycloudflare\.com/);
  if (match && !urlFound) {
    urlFound = true;
    const url = match[0];
    console.log('\x1b[32m%s\x1b[0m', '\n================================================================');
    console.log('\x1b[1m\x1b[36m%s\x1b[0m', ' 🚀 TÚNEL ACTIVO - LYSANDRI EXECUTIVE');
    console.log('\x1b[32m%s\x1b[0m', '================================================================');
    console.log('\x1b[1m\x1b[32m%s\x1b[0m', ' 📌 ENLACES DIRECTOS PARA PRUEBAS:');
    console.log('');
    console.log('\x1b[1m\x1b[33m%s\x1b[0m', ' 1. 🛒 Marketplace & Compras:');
    console.log(`    ${url}/`);
    console.log('');
    console.log('\x1b[1m\x1b[33m%s\x1b[0m', ' 2. 📊 Área Contable & Facturación SUNAT:');
    console.log(`    ${url}/contabilidad`);
    console.log('\x1b[90m%s\x1b[0m', '    ↳ Acceso directo contable (PIN: LYS-AUDIT-2026-SECURE | Usuario: admin / contabilidad)');
    console.log('');
    console.log('\x1b[1m\x1b[33m%s\x1b[0m', ' 3. 🔐 Intranet de Colaboradores:');
    console.log(`    ${url}/intranet`);
    console.log('');
    console.log('\x1b[1m\x1b[33m%s\x1b[0m', ' 4. 👤 Registro de Administradores & Personal:');
    console.log(`    ${url}/registro`);
    console.log('');
    console.log('\x1b[32m%s\x1b[0m', '----------------------------------------------------------------');
    console.log('\x1b[90m%s\x1b[0m', ' (Copia y comparte cualquiera de estos enlaces directamente)');
    console.log('\x1b[90m%s\x1b[0m', ' Presiona Ctrl + C en cualquier momento para detener el túnel.');
    console.log('\x1b[32m%s\x1b[0m\n', '================================================================\n');
  }

  // Print errors or info cleanly
  if (text.includes('ERR') || text.includes('error')) {
    process.stderr.write(text);
  }
}

tunnel.stdout.on('data', processOutput);
tunnel.stderr.on('data', processOutput);

tunnel.on('close', (code) => {
  console.log(`\nTúnel finalizado (código: ${code}).`);
});

process.on('SIGINT', () => {
  tunnel.kill();
  process.exit(0);
});
