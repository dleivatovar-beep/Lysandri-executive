import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const frontendDir = __dirname;
const evidenceDir = path.resolve(frontendDir, '../backend/evidencias-auth/ejecucion-20261103-cierre-confirmacion-disponibilidad-izipay');

fs.mkdirSync(evidenceDir, { recursive: true });

const commands = [
  {
    id: '01',
    name: 'test-checkout-listeners-lifecycle',
    cmd: process.platform === 'win32' ? 'node.exe' : 'node',
    args: ['test-checkout-listeners-lifecycle.js'],
    cwd: frontendDir,
    logFile: '01-test-checkout-listeners-lifecycle.log',
    description: 'Pruebas de ciclo de vida de listeners, renderElements y confirmación documentada de disponibilidad del SDK izipay',
  },
  {
    id: '02',
    name: 'test-accounting-view-interaction',
    cmd: process.platform === 'win32' ? 'node.exe' : 'node',
    args: ['test-accounting-view-interaction.js'],
    cwd: frontendDir,
    logFile: '02-test-accounting-view-interaction.log',
    description: 'Pruebas de interacción de vista contable (descarte de respuestas fuera de orden, anulación y sesión)',
  },
  {
    id: '03',
    name: 'test-accounting-and-admin-services',
    cmd: process.platform === 'win32' ? 'node.exe' : 'node',
    args: ['test-accounting-and-admin-services.js'],
    cwd: frontendDir,
    logFile: '03-test-accounting-and-admin-services.log',
    description: 'Pruebas unitarias de servicios de contabilidad y administración',
  },
  {
    id: '04',
    name: 'test-checkout-modal-component',
    cmd: process.platform === 'win32' ? 'node.exe' : 'node',
    args: ['test-checkout-modal-component.js'],
    cwd: frontendDir,
    logFile: '04-test-checkout-modal-component.log',
    description: 'Pruebas de componentes DOM de CheckoutModal (idempotencia, persistencia, estados y errores)',
  },
  {
    id: '05',
    name: 'npm-run-build',
    cmd: process.platform === 'win32' ? 'cmd.exe' : 'npm',
    args: process.platform === 'win32' ? ['/d', '/s', '/c', 'npm run build'] : ['run', 'build'],
    cwd: frontendDir,
    logFile: '05-npm-run-build-typescript-vite.log',
    description: 'Compilación TypeScript y empaquetado de producción con Vite',
  },
];

async function runCommand(def) {
  const fullCommandLine = `${def.cmd} ${def.args.join(' ')}`;
  const startTime = new Date().toISOString();
  console.log(`\n================================================================================`);
  console.log(`[${def.id}] EJECUTANDO: ${fullCommandLine}`);
  console.log(`DIRECTORIO: ${def.cwd}`);
  console.log(`FECHA/HORA: ${startTime}`);
  console.log(`================================================================================`);

  return new Promise((resolve) => {
    let stdoutBuffer = '';
    let stderrBuffer = '';

    const child = spawn(def.cmd, def.args, {
      cwd: def.cwd,
      env: { ...process.env, CI: 'true', FORCE_COLOR: '0' },
      shell: false,
    });

    child.stdout.on('data', (data) => {
      const text = data.toString();
      stdoutBuffer += text;
      process.stdout.write(text);
    });

    child.stderr.on('data', (data) => {
      const text = data.toString();
      stderrBuffer += text;
      process.stderr.write(text);
    });

    child.on('error', (err) => {
      const endTime = new Date().toISOString();
      stderrBuffer += `\nERROR AL INICIAR PROCESO: ${err.message}\n`;
      resolve({
        id: def.id,
        name: def.name,
        command: fullCommandLine,
        cwd: def.cwd,
        description: def.description,
        startTime,
        endTime,
        exitCode: 1,
        stdout: stdoutBuffer,
        stderr: stderrBuffer,
        logFile: def.logFile,
      });
    });

    child.on('close', (code) => {
      const endTime = new Date().toISOString();
      resolve({
        id: def.id,
        name: def.name,
        command: fullCommandLine,
        cwd: def.cwd,
        description: def.description,
        startTime,
        endTime,
        exitCode: code ?? 0,
        stdout: stdoutBuffer,
        stderr: stderrBuffer,
        logFile: def.logFile,
      });
    });
  });
}

function parseTestSummary(output, name) {
  if (name === 'test-checkout-listeners-lifecycle') {
    const match = output.match(/RESULTADO CICLO DE VIDA.*?IZIPAY:\s*(\d+)\/(\d+)\s*escenarios aprobados/i);
    if (match) {
      const passed = parseInt(match[1], 10);
      const total = parseInt(match[2], 10);
      return { passed, failed: total - passed, skipped: 0, total };
    }
  }
  if (name === 'test-accounting-view-interaction') {
    const match = output.match(/RESUMEN:\s*(\d+)\/(\d+)\s*pruebas de interacción frontend aprobadas/i);
    if (match) {
      const passed = parseInt(match[1], 10);
      const total = parseInt(match[2], 10);
      return { passed, failed: total - passed, skipped: 0, total };
    }
  }
  if (name === 'test-accounting-and-admin-services') {
    const match = output.match(/RESUMEN:\s*(\d+)\s*de\s*(\d+)\s*pruebas unitarias frontend aprobadas/i);
    if (match) {
      const passed = parseInt(match[1], 10);
      const total = parseInt(match[2], 10);
      return { passed, failed: total - passed, skipped: 0, total };
    }
  }
  if (name === 'test-checkout-modal-component') {
    const match = output.match(/RESULTADO UI:\s*(\d+)\/(\d+)\s*escenarios aprobados/i);
    if (match) {
      const passed = parseInt(match[1], 10);
      const total = parseInt(match[2], 10);
      return { passed, failed: total - passed, skipped: 0, total };
    }
  }
  if (name === 'npm-run-build') {
    const success = output.includes('built in') || output.includes('dist/index.html');
    return { buildSuccess: success, typeCheckSuccess: !output.includes('error TS') };
  }
  return null;
}

async function main() {
  const globalStart = new Date().toISOString();
  console.log(`Iniciando verificación integral del frontend: ${globalStart}`);
  console.log(`Directorio de evidencias: ${evidenceDir}\n`);

  const results = [];

  for (const cmdDef of commands) {
    const result = await runCommand(cmdDef);
    results.push(result);

    const logPath = path.join(evidenceDir, defToLogFileName(cmdDef.id, cmdDef.name));
    const logContent = [
      `================================================================================`,
      `EVIDENCIA DE EJECUCIÓN FRONTEND: ${cmdDef.name}`,
      `================================================================================`,
      `Comando exacto:        ${result.command}`,
      `Directorio:            ${result.cwd}`,
      `Fecha y hora inicio:   ${result.startTime}`,
      `Fecha y hora fin:      ${result.endTime}`,
      `Código de salida real: ${result.exitCode}`,
      `Descripción:           ${cmdDef.description}`,
      `================================================================================`,
      `SALIDA COMPLETA (STDOUT):`,
      `--------------------------------------------------------------------------------`,
      result.stdout || '(vacío)',
      `--------------------------------------------------------------------------------`,
      `SALIDA COMPLETA (STDERR):`,
      `--------------------------------------------------------------------------------`,
      result.stderr || '(vacío)',
      `================================================================================`,
    ].join('\n');

    fs.writeFileSync(logPath, logContent, 'utf8');

    if (result.exitCode !== 0) {
      console.error(`\n[FALLO CRÍTICO] El comando ${cmdDef.name} retornó código ${result.exitCode}`);
      process.exitCode = 1;
    }
  }

  const globalEnd = new Date().toISOString();

  // Generar resumen estructurado JSON
  const summaryData = {
    fechaEjecucionInicio: globalStart,
    fechaEjecucionFin: globalEnd,
    directorioEjecucion: frontendDir,
    directorioEvidencias: evidenceDir,
    comandos: results.map((r) => ({
      id: r.id,
      nombre: r.name,
      comando: r.command,
      directorio: r.cwd,
      inicio: r.startTime,
      fin: r.endTime,
      codigoSalida: r.exitCode,
      archivoLog: defToLogFileName(r.id, r.name),
      resumenCasos: parseTestSummary(r.stdout + r.stderr, r.name),
    })),
    estadoGlobal: results.every((r) => r.exitCode === 0) ? 'APROBADO_EXITOSO' : 'FALLIDO',
  };

  const jsonSummaryPath = path.join(evidenceDir, 'resumen-verificacion-frontend.json');
  fs.writeFileSync(jsonSummaryPath, JSON.stringify(summaryData, null, 2), 'utf8');

  // Generar registro consolidado de texto
  const txtSummaryPath = path.join(evidenceDir, 'registro-consolidado-frontend.log');
  let txtContent = [
    `================================================================================`,
    `REGISTRO CONSOLIDADO DE VERIFICACIÓN FRONTEND`,
    `Fecha de ejecución: ${globalStart} - ${globalEnd}`,
    `Directorio base:    ${frontendDir}`,
    `Estado global:      ${summaryData.estadoGlobal}`,
    `================================================================================`,
    ``,
    `RESUMEN DE COMANDOS Y CÓDIGOS DE SALIDA:`,
    `--------------------------------------------------------------------------------`,
  ];

  for (const r of summaryData.comandos) {
    const summaryStr = r.resumenCasos
      ? r.nombre === 'npm-run-build'
        ? `Build: ${r.resumenCasos.buildSuccess ? 'OK' : 'FAIL'} | TS: ${r.resumenCasos.typeCheckSuccess ? 'OK' : 'FAIL'}`
        : `Aprobados: ${r.resumenCasos.passed}, Fallidos: ${r.resumenCasos.failed}, Omitidos: ${r.resumenCasos.skipped}`
      : 'N/A';
    txtContent.push(`[${r.id}] ${r.nombre}`);
    txtContent.push(`    Comando:       ${r.comando}`);
    txtContent.push(`    Código salida: ${r.codigoSalida}`);
    txtContent.push(`    Detalle:       ${summaryStr}`);
    txtContent.push(`    Log:           ${r.archivoLog}`);
    txtContent.push(``);
  }

  txtContent.push(`================================================================================`);
  fs.writeFileSync(txtSummaryPath, txtContent.join('\n'), 'utf8');

  console.log(`\n================================================================================`);
  console.log(`RESUMEN FINAL DE VERIFICACIÓN:`);
  console.log(`Estado: ${summaryData.estadoGlobal}`);
  console.log(`Evidencias guardadas en: ${evidenceDir}`);
  console.log(`================================================================================\n`);
}

function defToLogFileName(id, name) {
  return `${id}-${name}.log`;
}

main().catch((err) => {
  console.error('Error fatal en runner:', err);
  process.exit(1);
});
