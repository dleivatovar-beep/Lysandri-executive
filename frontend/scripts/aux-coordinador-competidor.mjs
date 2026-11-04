/**
 * aux-coordinador-competidor.mjs
 * Subproceso auxiliar independiente para pruebas reales de concurrencia a nivel de sistema operativo.
 *
 * Utiliza directamente las funciones de producción de iniciar-entorno.js y se sincroniza
 * mediante barreras de archivo de precisión de milisegundo para competir en igualdad de condiciones.
 */

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import {
  conMutexDeBloqueo,
  conMutexDeBloqueoAsync,
  adquirirBloqueoCoordinador,
  liberarBloqueoCoordinador,
  actualizarBloqueoCoordinador,
  setStartedBackendRecord,
  setActiveLockSession,
  ejecutarLimpieza,
} from './iniciar-entorno.js';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i].startsWith('--')) {
      const key = args[i].slice(2);
      const val = args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : true;
      options[key] = val;
      if (val !== true) i++;
    }
  }
  return options;
}

async function esperarBarrera(barrierFile) {
  if (!barrierFile) return;

  // 1. Notificar que este proceso está listo y a la espera
  const readyFile = `${barrierFile}.ready.${process.pid}`;
  fs.writeFileSync(readyFile, JSON.stringify({ pid: process.pid, readyAt: Date.now() }), 'utf-8');

  // 2. Esperar archivo de arranque con timestamp sincronizado
  const startFile = `${barrierFile}.start`;
  const timeoutMs = 15000;
  const pollStart = Date.now();

  let targetTimeMs = 0;
  while (Date.now() - pollStart < timeoutMs) {
    if (fs.existsSync(startFile)) {
      try {
        const raw = fs.readFileSync(startFile, 'utf-8');
        const parsed = JSON.parse(raw);
        if (parsed && parsed.startTimeMs) {
          targetTimeMs = parsed.startTimeMs;
          break;
        }
      } catch (_) {}
    }
    await sleep(10);
  }

  // 3. Esperar al milisegundo exacto para disparar simultáneamente
  if (targetTimeMs > 0) {
    while (Date.now() < targetTimeMs) {
      // micro-espera
    }
  }
}

async function main() {
  const options = parseArgs();
  const mode = options.mode;
  const lockPath = options['lock-path'];
  const resultFile = options['result-file'];
  const barrierFile = options['barrier-file'];
  const holdMs = parseInt(options['hold-ms'] || '0', 10);
  const ownerToken = options['owner-token'] || crypto.randomUUID();
  const pid = parseInt(options.pid || String(process.pid), 10);
  const waitMs = parseInt(options['wait-ms'] || '3000', 10);
  const durationMs = parseInt(options['duration-ms'] || '5000', 10);
  const signalFile = options['signal-file'];
  const releaseSignal = options['release-signal'];
  const childPid = parseInt(options['child-pid'] || '0', 10);

  if (mode === 'compete-lock' || mode === 'compete-recovery') {
    await esperarBarrera(barrierFile);

    const lockResult = adquirirBloqueoCoordinador({
      lockPath,
      ownerToken,
      pid,
    });

    if (resultFile) {
      fs.writeFileSync(
        resultFile,
        JSON.stringify({
          pid: process.pid,
          configuredPid: pid,
          adquirido: lockResult.adquirido,
          ownerToken: lockResult.ownerToken,
          razon: lockResult.razon || null,
          timestamp: Date.now(),
        }, null, 2),
        'utf-8'
      );
    }

    if (lockResult.adquirido && holdMs > 0) {
      await sleep(holdMs);
    }
    process.exit(0);
  }

  if (mode === 'hold-mutex') {
    try {
      conMutexDeBloqueo(
        lockPath,
        () => {
          if (signalFile) {
            fs.writeFileSync(
              signalFile,
              JSON.stringify({ acquired: true, pid: process.pid, acquiredAt: Date.now() }),
              'utf-8'
            );
          }
          const holdStart = Date.now();
          while (Date.now() - holdStart < durationMs) {
            if (releaseSignal && fs.existsSync(releaseSignal)) {
              break;
            }
            const till = Date.now() + 50;
            while (Date.now() < till) {}
          }
          return { done: true, heldMs: Date.now() - holdStart };
        },
        { maxWaitMs: 5000, pid: process.pid }
      );

      if (resultFile) {
        fs.writeFileSync(
          resultFile,
          JSON.stringify({ pid: process.pid, success: true, releasedAt: Date.now() }),
          'utf-8'
        );
      }
      process.exit(0);
    } catch (err) {
      if (resultFile) {
        fs.writeFileSync(
          resultFile,
          JSON.stringify({ pid: process.pid, success: false, error: err.message }),
          'utf-8'
        );
      }
      process.exit(1);
    }
  }

  if (mode === 'try-acquire-mutex') {
    if (barrierFile) {
      await esperarBarrera(barrierFile);
    }
    let acquired = false;
    let errorMsg = null;
    try {
      conMutexDeBloqueo(
        lockPath,
        () => {
          acquired = true;
          return { acquired: true };
        },
        { maxWaitMs: waitMs, pid: process.pid }
      );
    } catch (err) {
      acquired = false;
      errorMsg = err.message;
    }

    if (resultFile) {
      fs.writeFileSync(
        resultFile,
        JSON.stringify({
          pid: process.pid,
          acquired,
          error: errorMsg,
          timestamp: Date.now(),
        }, null, 2),
        'utf-8'
      );
    }
    process.exit(0);
  }

  if (mode === 'incomplete-cleanup') {
    const lockRes = adquirirBloqueoCoordinador({
      lockPath,
      ownerToken,
      pid: process.pid,
    });

    if (!lockRes.adquirido) {
      if (resultFile) {
        fs.writeFileSync(
          resultFile,
          JSON.stringify({ success: false, error: 'No pudo adquirir bloqueo inicial' }),
          'utf-8'
        );
      }
      process.exit(1);
    }

    setActiveLockSession(lockPath, ownerToken);

    // Registrar proceso backend cuyo hijo permanece vivo/no terminable
    setStartedBackendRecord({
      wrapperPid: childPid || process.pid,
      knownPids: new Set([childPid || process.pid]),
      wrapperCmdPattern: 'node',
      childCmdPattern: 'node',
    });

    // Ejecutar limpieza simulando un proceso que no termina
    const cleanupRes = await ejecutarLimpieza({
      exitOnFinish: false,
      terminarArbolFn: () => ({
        exito: false,
        pidsTerminados: [],
        pidsVivos: [childPid || process.pid],
        pidsDesconocidos: [],
        pidsRechazados: [],
      }),
    });

    if (resultFile) {
      fs.writeFileSync(
        resultFile,
        JSON.stringify({
          pid: process.pid,
          ownerToken,
          cleanupSuccess: cleanupRes.exito,
          bloqueoLiberado: cleanupRes.bloqueoLiberado,
          bloqueoActualizado: cleanupRes.bloqueoActualizado,
          pidsVivos: cleanupRes.pidsVivos,
        }, null, 2),
        'utf-8'
      );
    }

    process.exit(cleanupRes.exito ? 0 : 1);
  }

  if (mode === 'attempt-start') {
    const lockRes = adquirirBloqueoCoordinador({
      lockPath,
      ownerToken,
      pid: process.pid,
    });

    if (resultFile) {
      fs.writeFileSync(
        resultFile,
        JSON.stringify({
          pid: process.pid,
          adquirido: lockRes.adquirido,
          razon: lockRes.razon || null,
          ownerToken: lockRes.ownerToken,
        }, null, 2),
        'utf-8'
      );
    }
    process.exit(0);
  }

  console.error(`Modo desconocido: ${mode}`);
  process.exit(1);
}

main().catch((err) => {
  console.error('Error en aux-coordinador-competidor:', err);
  process.exit(1);
});

