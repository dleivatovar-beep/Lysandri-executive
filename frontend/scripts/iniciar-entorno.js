#!/usr/bin/env node
/**
 * iniciar-entorno.js
 * Coordinador de inicio único del entorno de desarrollo de Lysandri Executive.
 *
 * Comprueba e inicia en orden estricto (o reutiliza instancias existentes válidas):
 * 1. Docker Desktop (motor Linux).
 * 2. Contenedor existente lysandri-postgres-clone (127.0.0.1:5434).
 * 3. PostgreSQL del clon mediante bucle de disponibilidad y verificación autenticada estricta.
 * 4. Backend local con perfil clone en el puerto 8080 (o reutiliza instancia verificada).
 * 5. Servidor Vite en el puerto 3000 con validación estricta de identidad de proyecto.
 * 6. Navegador en http://127.0.0.1:3000 (abierto una sola vez).
 *
 * Restricciones obligatorias:
 * - Sin conexión ni modificación de la base original (puerto 5432).
 * - No crea clones nuevos ni borra volúmenes ni modifica contenedores.
 * - Modo --solo-verificar es estrictamente de solo lectura (cero procesos dejados).
 * - Limpieza limpia en Ctrl+C (únicamente de procesos iniciados por esta ejecución).
 */

import { exec, execSync, spawn } from 'child_process';
import http from 'http';
import net from 'net';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import os from 'os';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Directorios base del proyecto
export const FRONTEND_DIR = path.resolve(__dirname, '..');
export const ROOT_DIR = path.resolve(FRONTEND_DIR, '..');
export const BACKEND_DIR = path.resolve(ROOT_DIR, 'backend');
export const INICIAR_BACKEND_SCRIPT = path.resolve(ROOT_DIR, 'iniciar-backend.ps1');
export const JAR_PATH = path.resolve(BACKEND_DIR, 'target', 'lysandri-backend-1.0.0-SNAPSHOT.jar');
export const LOCK_FILE = path.resolve(FRONTEND_DIR, '.dev-orchestrator.lock');

// Firma determinística del workspace (sin exponer rutas absolutas ni secretos en HTTP)
export const WORKSPACE_SIGNATURE = crypto
  .createHash('sha256')
  .update(ROOT_DIR.toLowerCase().replace(/\\/g, '/'))
  .digest('hex')
  .slice(0, 16);

// Colores de consola
export const colors = {
  reset: '\x1b[0m',
  cyan: '\x1b[36m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  gray: '\x1b[90m',
  bold: '\x1b[1m',
};

export function logStep(step, message) {
  console.log(`${colors.cyan}${colors.bold}[${step}]${colors.reset} ${colors.yellow}${message}${colors.reset}`);
}

export function logOk(message) {
  console.log(`  ${colors.green}✔ ${message}${colors.reset}`);
}

export function logInfo(message) {
  console.log(`  ${colors.gray}ℹ ${message}${colors.reset}`);
}

export function logWarn(message) {
  console.log(`  ${colors.yellow}⚠ ${message}${colors.reset}`);
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function sanitizarSalida(texto) {
  if (!texto) return '';
  return texto
    .replace(/(password|pass|secret|token|jwt|credential)=([^\s;&]+)/gi, '$1=***')
    .replace(/(PGPASSWORD|CLONE_APP_PASSWORD|CLONE_ADMIN_PASSWORD)\s*=\s*[^\s\r\n]+/gi, '$1=***');
}

// Ejecución sincrónica protegida
export function runCmd(cmd, options = {}) {
  try {
    const stdout = execSync(cmd, {
      encoding: 'utf-8',
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
      ...options,
    });
    return { success: true, stdout: stdout.trim(), stderr: '' };
  } catch (err) {
    return {
      success: false,
      stdout: (err.stdout || '').toString().trim(),
      stderr: (err.stderr || err.message || '').toString().trim(),
      code: err.status || 1,
    };
  }
}

// Comprobación de socket TCP nativo
export function checkTcpPort(host, port, timeoutMs = 1500) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let isResolved = false;

    socket.setTimeout(timeoutMs);

    socket.once('connect', () => {
      isResolved = true;
      socket.destroy();
      resolve(true);
    });

    socket.once('timeout', () => {
      if (!isResolved) {
        isResolved = true;
        socket.destroy();
        resolve(false);
      }
    });

    socket.once('error', () => {
      if (!isResolved) {
        isResolved = true;
        socket.destroy();
        resolve(false);
      }
    });

    socket.connect(port, host);
  });
}

// Comprobación de petición HTTP
export function httpGet(url, timeoutMs = 2500) {
  return new Promise((resolve) => {
    let parsedUrl;
    try {
      parsedUrl = new URL(url);
    } catch (_) {
      return resolve({ ok: false, status: 0, body: '', headers: {} });
    }

    const req = http.request(
      {
        hostname: parsedUrl.hostname,
        port: parsedUrl.port || 80,
        path: parsedUrl.pathname + parsedUrl.search,
        method: 'GET',
        timeout: timeoutMs,
        headers: {
          Accept: '*/*',
          'User-Agent': 'Lysandri-Environment-Orchestrator/2.0',
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => {
          data += chunk;
        });
        res.on('end', () => {
          resolve({
            ok: res.statusCode >= 200 && res.statusCode < 400,
            status: res.statusCode,
            statusCode: res.statusCode,
            body: data,
            headers: res.headers,
          });
        });
      }
    );

    req.on('timeout', () => {
      req.destroy();
      resolve({ ok: false, status: 0, body: '', headers: {} });
    });

    req.on('error', () => {
      resolve({ ok: false, status: 0, body: '', headers: {} });
    });

    req.end();
  });
}

// -----------------------------------------------------------------------------
// INSPECCIÓN DE PROCESOS EN WINDOWS
// -----------------------------------------------------------------------------
export function buscarProcesoEnPuerto(port) {
  const script = `Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess`;
  const res = runCmd(`powershell -NoProfile -Command "${script}"`);
  if (res.success && res.stdout) {
    const pidNum = parseInt(res.stdout.split(/\r?\n/)[0].trim(), 10);
    return isNaN(pidNum) ? null : pidNum;
  }
  return null;
}

export function obtenerDetallesProcesoEnPuerto(port) {
  const pid = buscarProcesoEnPuerto(port);
  if (!pid) return { pid: null, name: '', cmdLine: '' };

  const nameRes = runCmd(`powershell -NoProfile -Command "(Get-Process -Id ${pid} -ErrorAction SilentlyContinue).ProcessName"`);
  const cmdRes = runCmd(`powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}').CommandLine"`);

  return {
    pid,
    name: nameRes.success ? nameRes.stdout : '',
    cmdLine: cmdRes.success ? cmdRes.stdout : '',
  };
}

// -----------------------------------------------------------------------------
// PUNTO 4: VALIDACIÓN EXACTA DEL MAPEO DEL CLON
// -----------------------------------------------------------------------------
export function validarMapeoPuertosClon(inspectData) {
  if (!inspectData || typeof inspectData !== 'object') {
    return {
      valido: false,
      razon: 'Datos de inspección de Docker inválidos o vacíos.',
    };
  }

  const status = inspectData.State?.Status || inspectData.status || '';
  const isRunning = status === 'running';

  const portBindings = inspectData.HostConfig?.PortBindings;
  if (!portBindings || typeof portBindings !== 'object') {
    return {
      valido: false,
      razon: "No se encontró 'HostConfig.PortBindings' en la configuración del contenedor.",
    };
  }

  // Verificar si intenta publicar en el puerto 5432 del host en cualquier enlace
  const allHostBindings = [
    ...Object.values(portBindings || {}),
    ...(inspectData.NetworkSettings?.Ports ? Object.values(inspectData.NetworkSettings.Ports) : []),
  ]
    .flat()
    .filter(Boolean);

  for (const b of allHostBindings) {
    if (String(b.HostPort).trim() === '5432') {
      return {
        valido: false,
        razon: 'Violación de seguridad: El contenedor intenta publicar en el puerto 5432 del host (reservado para la base original).',
      };
    }
  }

  // Debe mapear el puerto interno 5432/tcp
  const bindingKeys = Object.keys(portBindings);
  if (!bindingKeys.includes('5432/tcp')) {
    return {
      valido: false,
      razon: `Puerto interno no autorizado o ausente: Se requiere mapear '5432/tcp', pero se encontró [${bindingKeys.join(', ') || 'ninguno'}].`,
    };
  }

  // Rechazar puertos internos adicionales mapeados
  for (const k of bindingKeys) {
    const list = portBindings[k];
    if (k !== '5432/tcp' && Array.isArray(list) && list.length > 0) {
      return {
        valido: false,
        razon: `Mapeo adicional no autorizado: Se detectó publicación en puerto interno '${k}'. Solo se autoriza 5432/tcp.`,
      };
    }
  }

  const bindings = portBindings['5432/tcp'];
  if (!Array.isArray(bindings) || bindings.length !== 1) {
    return {
      valido: false,
      razon: `Mapeo ambiguo o ausente: Se requiere exactamente 1 asignación para '5432/tcp', se encontraron ${bindings?.length ?? 0}.`,
    };
  }

  const binding = bindings[0];
  const hostPort = String(binding.HostPort || '').trim();
  const hostIp = String(binding.HostIp || '').trim();

  if (hostPort !== '5434') {
    return {
      valido: false,
      razon: `HostPort no coincide: Se exige '5434', se encontró '${hostPort}'.`,
    };
  }

  if (hostIp !== '127.0.0.1') {
    return {
      valido: false,
      razon: `HostIp no coincide: Se exige enlace exclusivo a '127.0.0.1', se encontró '${hostIp || '0.0.0.0 (todas las interfaces)'}'.`,
    };
  }

  // Si el contenedor está en ejecución, verificar también NetworkSettings.Ports
  if (isRunning) {
    const netPorts = inspectData.NetworkSettings?.Ports;
    if (!netPorts || typeof netPorts !== 'object') {
      return {
        valido: false,
        razon: 'El contenedor está en ejecución pero NetworkSettings.Ports no está disponible.',
      };
    }

    const netBindings = netPorts['5432/tcp'];
    if (!Array.isArray(netBindings) || netBindings.length !== 1) {
      return {
        valido: false,
        razon: `Publicación efectiva no encontrada en NetworkSettings.Ports para 5432/tcp (encontrados: ${netBindings?.length ?? 0}).`,
      };
    }

    const nb = netBindings[0];
    if (String(nb.HostPort || '').trim() !== '5434') {
      return {
        valido: false,
        razon: `Puerto publicado efectivo en NetworkSettings no coincide con 5434: '${nb.HostPort}'.`,
      };
    }
    if (String(nb.HostIp || '').trim() !== '127.0.0.1') {
      return {
        valido: false,
        razon: `IP publicada efectiva no coincide con 127.0.0.1: '${nb.HostIp}'.`,
      };
    }
  }

  return { valido: true };
}

// -----------------------------------------------------------------------------
// PUNTO 1: ESPERAR LA DISPONIBILIDAD REAL DE POSTGRESQL
// -----------------------------------------------------------------------------
export async function esperarDisponibilidadPostgres({
  cloneHost = '127.0.0.1',
  clonePort = 5434,
  cloneDatabase = 'lysandri_clone_db',
  timeoutMs = 45000,
  probeIntervalMs = 1500,
  tcpProbeTimeoutMs = 1200,
  commandTimeoutMs = 15000,
  scriptPath = INICIAR_BACKEND_SCRIPT,
  runCmdFn = runCmd,
  tcpProbeFn = checkTcpPort,
  onProgress = (elapsed = 0, total = 0) => { },
} = {}) {
  const startTime = Date.now();
  let lastError = null;

  while (Date.now() - startTime < timeoutMs) {
    const elapsedSec = Math.round((Date.now() - startTime) / 1000);
    const totalSec = Math.round(timeoutMs / 1000);
    onProgress(elapsedSec, totalSec);

    // 1. Probar conectividad TCP primero
    const tcpOpen = await tcpProbeFn(cloneHost, clonePort, tcpProbeTimeoutMs);
    if (!tcpOpen) {
      lastError = `No hay conexión TCP en ${cloneHost}:${clonePort}. PostgreSQL aún no acepta conexiones de red.`;
      await sleep(probeIntervalMs);
      continue;
    }

    // 2. Ejecutar verificación autenticada de iniciar-backend.ps1
    const psScript = scriptPath.replace(/\\/g, '/');
    const verifyCmd = `powershell -ExecutionPolicy Bypass -File "${psScript}" -SoloVerificarBaseDatos -CloneHost "${cloneHost}" -ClonePort ${clonePort} -CloneDatabase "${cloneDatabase}"`;

    const remainingMs = timeoutMs - (Date.now() - startTime);
    const thisTimeout = Math.min(commandTimeoutMs, Math.max(2000, remainingMs));
    const result = runCmdFn(verifyCmd, { timeout: thisTimeout });

    if (result.success) {
      return {
        listo: true,
        tiempoMs: Date.now() - startTime,
      };
    }

    const stderr = (result.stderr || result.stdout || '').toString();
    lastError = stderr;

    // 3. Evaluar si es un error permanente de configuración/credenciales
    if (
      stderr.includes('Fallo de autenticacion') ||
      stderr.includes('password authentication failed') ||
      stderr.includes('VIOLACION DE SEGURIDAD') ||
      stderr.includes('Base de datos no autorizada') ||
      stderr.includes('Falta la variable obligatoria') ||
      stderr.includes('Herramienta de verificacion no disponible') ||
      stderr.includes('Version de Java no soportada')
    ) {
      return {
        listo: false,
        errorPermanente: true,
        mensaje: `Error permanente de configuración o autenticación en PostgreSQL:\n${sanitizarSalida(stderr)}`,
      };
    }

    // 4. Error transitorio (starting up, recovery, reset), esperar e iterar
    await sleep(probeIntervalMs);
  }

  return {
    listo: false,
    errorPermanente: false,
    mensaje: `Tiempo de espera agotado (${Math.round(timeoutMs / 1000)}s): PostgreSQL en ${cloneHost}:${clonePort} (${cloneDatabase}) no completó su inicialización y verificación autenticada.\nÚltimo estado detectado:\n${sanitizarSalida(lastError || 'Sin respuesta')}`,
  };
}

export function esDescendiente(rootPid, candidatePid) {
  if (!rootPid || !candidatePid) return false;
  const desc = descubrirDescendientes(rootPid);
  return desc.some((d) => d && d.pid === candidatePid);
}

// -----------------------------------------------------------------------------
// PUNTO 2: IDENTIFICAR CORRECTAMENTE EL FRONTEND EXISTENTE
// -----------------------------------------------------------------------------
export async function verificarIdentidadFrontend({
  port = 3000,
  expectedSig = WORKSPACE_SIGNATURE,
  httpGetFn = httpGet,
  processInfoFn = obtenerDetallesProcesoEnPuerto,
  startedRecord = null,
} = {}) {
  const procInfo = await processInfoFn(port);
  if (!procInfo || !procInfo.pid) {
    return { activo: false, pid: null, esNuestro: false, estado: 'no_activo' };
  }

  const esProcesoPropio = startedRecord ? (
    startedRecord.wrapperPid === procInfo.pid ||
    (startedRecord.knownPids && startedRecord.knownPids.has(procInfo.pid)) ||
    (typeof esDescendiente === 'function' && esDescendiente(startedRecord.wrapperPid, procInfo.pid))
  ) : null;

  // Si se inició un proceso en esta sesión y el puerto está tomado por un PID que no pertenece a nuestro árbol:
  if (startedRecord && !esProcesoPropio) {
    return {
      activo: true,
      pid: procInfo.pid,
      esNuestro: false,
      estado: 'proceso_ajeno',
      procName: procInfo.name,
      conflicto: `Conflicto en puerto ${port}: El puerto está ocupado por un proceso ajeno (PID ${procInfo.pid}, ${procInfo.name || 'desconocido'}) que no pertenece al servidor Vite iniciado.`,
    };
  }

  // 1. Probar endpoint de identificación oficial en IPv4 127.0.0.1
  const devIdRes = await httpGetFn(`http://127.0.0.1:${port}/__lysandri_dev_id`, 2000);
  if (devIdRes.ok) {
    try {
      const data = JSON.parse(devIdRes.body);
      if (
        data.app === 'lysandri-executive' &&
        data.role === 'frontend-dev' &&
        data.workspaceSignature === expectedSig
      ) {
        return {
          activo: true,
          pid: procInfo.pid,
          esNuestro: true,
          estado: 'listo',
          procName: procInfo.name,
        };
      }
      return {
        activo: true,
        pid: procInfo.pid,
        esNuestro: false,
        estado: 'identidad_invalida',
        conflicto: `Conflicto en puerto ${port}: Datos de identificación inválidos (app='${data?.app}', role='${data?.role}', firma='${data?.workspaceSignature}' vs esperada '${expectedSig}').`,
      };
    } catch (_) {
      return {
        activo: true,
        pid: procInfo.pid,
        esNuestro: false,
        estado: 'identidad_invalida',
        conflicto: `Conflicto en puerto ${port}: La respuesta del endpoint /__lysandri_dev_id no contiene JSON válido.`,
      };
    }
  }

  // 2. Si el endpoint /__lysandri_dev_id aún no responde:
  // Distinguir si el puerto está ocupado por nuestro propio proceso inicializándose o por un proceso ajeno
  if (esProcesoPropio) {
    return {
      activo: true,
      pid: procInfo.pid,
      esNuestro: false,
      estado: 'inicializando',
      transitorio: true,
      procName: procInfo.name,
      conflicto: `El servidor Vite propio (PID ${procInfo.pid}) se encuentra inicializándose; endpoint /__lysandri_dev_id aún no disponible.`,
    };
  }

  // 3. Proceso ajeno ocupando el puerto
  return {
    activo: true,
    pid: procInfo.pid,
    esNuestro: false,
    estado: 'proceso_ajeno',
    procName: procInfo.name,
    conflicto: `Conflicto en puerto ${port}: El puerto está ocupado por un servicio ajeno (PID ${procInfo.pid}, ${procInfo.name || 'proceso desconocido'}). No responde a la firma de desarrollo autorizada de este proyecto.`,
  };
}

// -----------------------------------------------------------------------------
// TOKENIZACIÓN DE LÍNEA DE COMANDOS Y ASOCIACIÓN ESTRICTA DE ARGUMENTOS
// -----------------------------------------------------------------------------
export function tokenizarCmdLine(cmdLine) {
  if (!cmdLine || typeof cmdLine !== 'string') return [];
  const tokens = [];
  let current = '';
  let inQuotes = false;
  let quoteChar = '';

  for (let i = 0; i < cmdLine.length; i++) {
    const char = cmdLine[i];
    if (
      char === '\\' &&
      i + 1 < cmdLine.length &&
      (cmdLine[i + 1] === '"' || cmdLine[i + 1] === "'" || cmdLine[i + 1] === '\\')
    ) {
      current += cmdLine[i + 1];
      i++;
      continue;
    }
    if (inQuotes) {
      if (char === quoteChar) {
        inQuotes = false;
        quoteChar = '';
      } else {
        current += char;
      }
    } else if (char === '"' || char === "'") {
      inQuotes = true;
      quoteChar = char;
    } else if (/\s/.test(char)) {
      if (current.length > 0) {
        tokens.push(current);
        current = '';
      }
    } else {
      current += char;
    }
  }
  if (current.length > 0) {
    tokens.push(current);
  }
  return tokens;
}

// -----------------------------------------------------------------------------
// PUNTO 3: VALIDACIÓN EXACTA DEL BACKEND REUTILIZADO (CONFIGURACIÓN REAL)
// -----------------------------------------------------------------------------
export function analizarCommandLineBackend(cmdLine, {
  expectedJarPath = JAR_PATH,
  expectedHost = '127.0.0.1',
  expectedPort = 5434,
  expectedDb = 'lysandri_clone_db',
  expectedProfile = 'clone',
} = {}) {
  if (!cmdLine || typeof cmdLine !== 'string') {
    return { valido: false, razon: 'Línea de comandos no disponible o vacía.' };
  }

  // 1. Violación de seguridad global: rechazar puerto 5432 como destino en cualquier propiedad
  if (/(?:jdbc:postgresql:\/\/[^\s"',;]*:5432(?:\/|\b)|:5432\/)/i.test(cmdLine)) {
    return {
      valido: false,
      razon: 'Violación de seguridad: El proceso hace referencia al puerto 5432 (reservado para la base original).',
    };
  }

  const tokens = tokenizarCmdLine(cmdLine);

  // 2. Extraer propiedades clave asociadas estrictamente
  const profileMatches = [];
  const datasourceMatches = [];
  const flywayMatches = [];
  let jarPathFound = null;

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];

    // Perfiles de Spring: -Dspring.profiles.active=... o --spring.profiles.active=...
    const profMatch = token.match(/^(?:-D|--)spring\.profiles\.active=(.+)$/i);
    if (profMatch) {
      profileMatches.push(profMatch[1]);
      continue;
    }

    // Datasource URL: -Dspring.datasource.url=... o --spring.datasource.url=...
    const dsMatch = token.match(/^(?:-D|--)spring\.datasource\.url=(.+)$/i);
    if (dsMatch) {
      datasourceMatches.push(dsMatch[1]);
      continue;
    }

    // Flyway URL: -Dspring.flyway.url=... o --spring.flyway.url=...
    const fwMatch = token.match(/^(?:-D|--)spring\.flyway\.url=(.+)$/i);
    if (fwMatch) {
      flywayMatches.push(fwMatch[1]);
      continue;
    }

    // JAR path: -jar <ruta> o -jar=<ruta>
    if (token.toLowerCase() === '-jar' && i + 1 < tokens.length) {
      jarPathFound = tokens[i + 1];
      i++;
      continue;
    }
    const jarEqMatch = token.match(/^-jar=(.+)$/i);
    if (jarEqMatch) {
      jarPathFound = jarEqMatch[1];
      continue;
    }
  }

  // 3. Validar perfil requerido
  if (profileMatches.length === 0) {
    return { valido: false, razon: `Perfil de Spring ausente en la línea de comandos (se exige profile '${expectedProfile}').` };
  }
  const uniqueProfiles = [...new Set(profileMatches)];
  if (uniqueProfiles.length > 1) {
    return {
      valido: false,
      razon: `Configuración contradictoria: Múltiples definiciones de perfil encontradas (${uniqueProfiles.join(', ')}).`,
    };
  }
  const activeProfiles = uniqueProfiles[0].split(',').map((p) => p.trim());
  if (!activeProfiles.includes(expectedProfile)) {
    return {
      valido: false,
      razon: `Perfil de Spring '${uniqueProfiles[0]}' no incluye el perfil requerido '${expectedProfile}'.`,
    };
  }

  // 4. Validar artefacto JAR
  if (!jarPathFound) {
    return { valido: false, razon: 'Argumento -jar ausente en la línea de comandos.' };
  }
  const normalizedJar = path.resolve(jarPathFound).toLowerCase().replace(/\\/g, '/');
  const normalizedExpectedJar = path.resolve(expectedJarPath).toLowerCase().replace(/\\/g, '/');
  if (normalizedJar !== normalizedExpectedJar) {
    return {
      valido: false,
      razon: `El artefacto JAR no corresponde a este proyecto (encontrado: '${path.basename(jarPathFound)}' en ruta no autorizada).`,
    };
  }

  // 5. Validar explícitamente spring.datasource.url
  if (datasourceMatches.length === 0) {
    return {
      valido: false,
      razon: 'Argumentos insuficientes: no se especificó la propiedad spring.datasource.url en la línea de comandos para verificar el destino.',
    };
  }
  const uniqueDs = [...new Set(datasourceMatches)];
  if (uniqueDs.length > 1) {
    return {
      valido: false,
      razon: 'Configuración contradictoria: Se encontraron múltiples valores dispares para spring.datasource.url.',
    };
  }

  const dsUrl = uniqueDs[0];
  const urlPattern = /^jdbc:postgresql:\/\/([a-zA-Z0-9.-]+)(?::(\d+))?\/([a-zA-Z0-9_.-]+)(?:\?.*)?$/i;
  const match = dsUrl.match(urlPattern);
  if (!match) {
    return {
      valido: false,
      razon: 'URL de conexión JDBC malformada en spring.datasource.url.',
    };
  }

  const host = match[1];
  const port = match[2] ? parseInt(match[2], 10) : 5432;
  const db = match[3];

  if (port === 5432) {
    return {
      valido: false,
      razon: 'Violación de seguridad: El proceso apunta al puerto 5432 (reservado para la base original).',
    };
  }
  if (host !== expectedHost) {
    return {
      valido: false,
      razon: `Host de base de datos no coincide: Se exige '${expectedHost}', se encontró '${host}'.`,
    };
  }
  if (port !== expectedPort) {
    return {
      valido: false,
      razon: `Puerto de base de datos no coincide: Se exige '${expectedPort}', se encontró '${port}'.`,
    };
  }
  if (db !== expectedDb) {
    return {
      valido: false,
      razon: `Base de datos no coincide: Se exige '${expectedDb}', se encontró '${db}'.`,
    };
  }

  // 6. Validar spring.flyway.url si se encuentra declarada
  if (flywayMatches.length > 0) {
    const uniqueFw = [...new Set(flywayMatches)];
    if (uniqueFw.length > 1) {
      return {
        valido: false,
        razon: 'Configuración contradictoria: Se encontraron múltiples valores dispares para spring.flyway.url.',
      };
    }
    const fwUrl = uniqueFw[0];
    const fwMatch = fwUrl.match(urlPattern);
    if (!fwMatch) {
      return {
        valido: false,
        razon: 'URL de conexión JDBC malformada en spring.flyway.url.',
      };
    }
    const fwHost = fwMatch[1];
    const fwPort = fwMatch[2] ? parseInt(fwMatch[2], 10) : 5432;
    const fwDb = fwMatch[3];

    if (fwPort === 5432) {
      return {
        valido: false,
        razon: 'Violación de seguridad: spring.flyway.url apunta al puerto 5432 (reservado para la base original).',
      };
    }
    if (fwHost !== expectedHost || fwPort !== expectedPort || fwDb !== expectedDb) {
      return {
        valido: false,
        razon: `Configuración contradictoria: spring.flyway.url no apunta al clon autorizado (${expectedHost}:${expectedPort}/${expectedDb}).`,
      };
    }
  }

  return {
    valido: true,
    host: expectedHost,
    port: expectedPort,
    database: expectedDb,
    profile: expectedProfile,
    jarPath: normalizedJar,
  };
}

export async function verificarIdentidadBackend({
  port = 8080,
  expectedJarPath = JAR_PATH,
  httpGetFn = httpGet,
  processInfoFn = obtenerDetallesProcesoEnPuerto,
} = {}) {
  const procInfo = await processInfoFn(port);
  if (!procInfo || !procInfo.pid) {
    return { activo: false, pid: null, esNuestro: false };
  }

  const cmdLine = procInfo.cmdLine || '';
  const analisis = analizarCommandLineBackend(cmdLine, { expectedJarPath });

  if (!analisis.valido) {
    return {
      activo: true,
      pid: procInfo.pid,
      esNuestro: false,
      conflicto: `Conflicto en puerto ${port}: Ocupado por PID ${procInfo.pid} (${procInfo.name || 'proceso'}). ${analisis.razon}`,
    };
  }

  // El análisis de argumentos es válido y seguro. Comprobar contrato HTTP en 127.0.0.1
  const resp = await httpGetFn(`http://127.0.0.1:${port}/api/v1/orders/cuentas-bancarias`, 2500);
  if (resp.ok && (resp.status === undefined || resp.status === 200 || resp.statusCode === 200)) {
    let validJson = false;
    try {
      const data = JSON.parse(resp.body);
      if (Array.isArray(data) || (data && typeof data === 'object')) validJson = true;
    } catch (_) {
      if (resp.body && (resp.body.includes('LYSANDRI S.A.C.') || resp.body.includes('['))) validJson = true;
    }
    if (validJson) {
      return { activo: true, pid: procInfo.pid, esNuestro: true, detalles: analisis };
    }
  }

  const respProg = await httpGetFn(`http://127.0.0.1:${port}/api/v1/programas`, 2500);
  if (respProg.ok && (respProg.status === undefined || respProg.status === 200 || respProg.statusCode === 200)) {
    return { activo: true, pid: procInfo.pid, esNuestro: true, detalles: analisis };
  }

  return {
    activo: true,
    pid: procInfo.pid,
    esNuestro: false,
    conflicto: `Conflicto en puerto ${port}: El backend no responde al contrato esperado de API Lysandri.`,
  };
}

export function verificarManifiestoJar(jarPath = JAR_PATH, runCmdFn = runCmd) {
  if (!fs.existsSync(jarPath)) {
    return { ejecutable: false, razon: `El archivo JAR no existe en '${jarPath}'.` };
  }
  try {
    const jarPosix = jarPath.replace(/\\/g, '/');
    let res = runCmdFn(`tar -xf "${jarPosix}" -O META-INF/MANIFEST.MF`);
    if ((!res.success || !res.stdout) && runCmdFn !== runCmd) {
      try { res = runCmd(`tar -xf "${jarPosix}" -O META-INF/MANIFEST.MF`); } catch (_) {}
    }
    if (!res.success || !res.stdout) {
      return { ejecutable: false, razon: 'No se pudo inspeccionar META-INF/MANIFEST.MF dentro del JAR.' };
    }
    const stdout = res.stdout;
    const hasMainClass = stdout.includes('Main-Class: org.springframework.boot.loader.launch.JarLauncher') ||
      stdout.includes('Main-Class: org.springframework.boot.loader.JarLauncher');
    const hasStartClass = stdout.includes('Start-Class: com.lysandri.LysandriApplication');
    if (!hasMainClass || !hasStartClass) {
      return {
        ejecutable: false,
        razon: 'El JAR no contiene las cabeceras ejecutables de Spring Boot (Main-Class / Start-Class).',
      };
    }
    return { ejecutable: true, razon: null };
  } catch (err) {
    return { ejecutable: false, razon: err.message };
  }
}

export function compilarBackendJar({ backendDir = BACKEND_DIR, runCmdFn = runCmd } = {}) {
  const mvnCmd = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';
  const compileCmd = `${mvnCmd} package -DskipTests`;
  return runCmdFn(compileCmd, { cwd: backendDir, timeout: 180000 });
}

// -----------------------------------------------------------------------------
// COMPROBACIÓN DE ARTEFACTO JAR
// -----------------------------------------------------------------------------
export function comprobarJarActualizado({
  backendDir = BACKEND_DIR,
  jarPath = JAR_PATH,
  fsModule = fs,
  runCmdFn = runCmd,
} = {}) {
  if (!fsModule.existsSync(jarPath)) {
    return {
      existe: false,
      esEjecutable: false,
      errorManifiesto: null,
      desactualizado: false,
      archivo: null,
      error: `No se encontró el archivo JAR en '${jarPath}'. Compile con: cd backend && ./mvnw package`,
    };
  }

  const manifest = verificarManifiestoJar(jarPath, runCmdFn);
  const jarStat = fsModule.statSync(jarPath);
  const jarMtime = jarStat.mtimeMs;

  let masReciente = null;
  let masRecienteMtime = 0;

  function escanear(dir) {
    if (!fsModule.existsSync(dir)) return;
    const entries = fsModule.readdirSync(dir, { withFileTypes: true });
    for (const ent of entries) {
      const fullPath = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        escanear(fullPath);
      } else if (ent.isFile()) {
        const ext = path.extname(ent.name).toLowerCase();
        if (['.java', '.sql', '.yml', '.yaml', '.xml', '.properties'].includes(ext)) {
          const st = fsModule.statSync(fullPath);
          if (st.mtimeMs > jarMtime && st.mtimeMs > masRecienteMtime) {
            masRecienteMtime = st.mtimeMs;
            masReciente = fullPath;
          }
        }
      }
    }
  }

  const srcDir = path.resolve(backendDir, 'src');
  escanear(srcDir);
  const pomPath = path.resolve(backendDir, 'pom.xml');
  if (fsModule.existsSync(pomPath)) {
    const st = fsModule.statSync(pomPath);
    if (st.mtimeMs > jarMtime && st.mtimeMs > masRecienteMtime) {
      masRecienteMtime = st.mtimeMs;
      masReciente = pomPath;
    }
  }

  return {
    existe: true,
    esEjecutable: manifest.ejecutable,
    errorManifiesto: manifest.razon,
    desactualizado: masReciente !== null,
    archivo: masReciente,
    jarFecha: jarStat.mtime,
  };
}

// -----------------------------------------------------------------------------
// PUNTO 3: MODO --solo-verificar (ESTRICTAMENTE DE SOLO LECTURA)
// -----------------------------------------------------------------------------
export async function ejecutarSoloVerificar({
  permitirJarDesactualizado = false,
  runCmdFn = runCmd,
  httpGetFn = httpGet,
  tcpProbeFn = checkTcpPort,
  processInfoFn = obtenerDetallesProcesoEnPuerto,
} = {}) {
  console.log(`\n${colors.cyan}${colors.bold}=== MODO DE SOLO LECTURA: --solo-verificar ===${colors.reset}`);
  console.log(`${colors.gray}Comprobando configuración, dependencias, identidades y disponibilidad sin iniciar nada...${colors.reset}\n`);

  const faltantes = [];

  // 1. Motor Docker
  const dockerVer = runCmdFn('docker version --format "{{.Server.Os}}"');
  if (!dockerVer.success || !dockerVer.stdout.toLowerCase().includes('linux')) {
    faltantes.push('Docker Desktop: Motor Linux no está en ejecución. Inicie Docker Desktop manualmente.');
  } else {
    logOk('Docker Desktop: Motor Linux activo y respondiendo.');
  }

  // 2. Contenedor lysandri-postgres-clone
  const inspectRes = runCmdFn('docker inspect lysandri-postgres-clone');
  if (!inspectRes.success || !inspectRes.stdout) {
    faltantes.push("Contenedor 'lysandri-postgres-clone': No existe.");
  } else {
    let inspectObj = null;
    try {
      const parsed = JSON.parse(inspectRes.stdout);
      inspectObj = Array.isArray(parsed) ? parsed[0] : parsed;
    } catch (_) { }

    if (!inspectObj) {
      faltantes.push("Contenedor 'lysandri-postgres-clone': No se pudo interpretar la respuesta JSON de docker inspect.");
    } else {
      const status = inspectObj.State?.Status || '';
      if (status !== 'running') {
        faltantes.push(`Contenedor 'lysandri-postgres-clone': Está detenido (Estado: '${status}').`);
      } else {
        logOk("Contenedor 'lysandri-postgres-clone': En ejecución.");
      }

      // Validar mapeo de puertos estricto
      const valMapeo = validarMapeoPuertosClon(inspectObj);
      if (!valMapeo.valido) {
        faltantes.push(`Mapeo de puertos del contenedor inválido: ${valMapeo.razon}`);
      } else {
        logOk('Mapeo de puertos del clon verificado exactamente en 127.0.0.1:5434.');
      }
    }
  }

  // 3. PostgreSQL TCP y autenticación
  const pgTcp = await tcpProbeFn('127.0.0.1', 5434, 1500);
  if (!pgTcp) {
    faltantes.push('PostgreSQL: Puerto TCP 127.0.0.1:5434 cerrado o inaccesible.');
  } else {
    const psScript = INICIAR_BACKEND_SCRIPT.replace(/\\/g, '/');
    const verifyCmd = `powershell -ExecutionPolicy Bypass -File "${psScript}" -SoloVerificarBaseDatos`;
    const verifyRes = runCmdFn(verifyCmd, { timeout: 10000 });
    if (!verifyRes.success) {
      faltantes.push(`PostgreSQL: Fallo de autenticación en 127.0.0.1:5434:\n${sanitizarSalida(verifyRes.stderr || verifyRes.stdout)}`);
    } else {
      logOk('PostgreSQL: Conexión TCP e identidades autenticadas confirmadas en 127.0.0.1:5434.');
    }
  }

  // 4. Artefacto JAR
  const estadoJar = comprobarJarActualizado({ runCmdFn });
  if (!estadoJar.existe) {
    faltantes.push(`Backend JAR: ${estadoJar.error}`);
  } else if (!estadoJar.esEjecutable) {
    faltantes.push(`Backend JAR: ${estadoJar.errorManifiesto}`);
  } else if (estadoJar.desactualizado && !permitirJarDesactualizado) {
    faltantes.push(`Backend JAR: Desactualizado respecto a '${path.basename(estadoJar.archivo)}'. Recompile con: cd backend && ./mvnw package`);
  } else {
    logOk('Backend JAR: Presente, ejecutable y válido.');
  }

  // 5. Backend en puerto 8080
  const backendIdent = await verificarIdentidadBackend({ port: 8080, httpGetFn, processInfoFn });
  if (!backendIdent.activo) {
    faltantes.push('Backend HTTP: Puerto 8080 no está en escucha.');
  } else if (!backendIdent.esNuestro) {
    faltantes.push(`Backend HTTP: ${backendIdent.conflicto}`);
  } else {
    logOk(`Backend HTTP: Activo y verificado en puerto 8080 (PID ${backendIdent.pid}).`);
  }

  // 6. Frontend en puerto 3000
  const frontendIdent = await verificarIdentidadFrontend({ port: 3000, httpGetFn, processInfoFn });
  if (!frontendIdent.activo) {
    faltantes.push('Frontend Vite: Puerto 3000 no está en escucha.');
  } else if (!frontendIdent.esNuestro) {
    faltantes.push(`Frontend Vite: ${frontendIdent.conflicto}`);
  } else {
    logOk(`Frontend Vite: Activo y verificado en puerto 3000 (PID ${frontendIdent.pid}).`);
  }

  // Resultado
  if (faltantes.length > 0) {
    console.error(`\n${colors.red}${colors.bold}✘ REQUISITOS FALTANTES O NO DISPONIBLES (${faltantes.length}):${colors.reset}`);
    for (const f of faltantes) {
      console.error(`  - ${f}`);
    }
    console.error(`\n${colors.gray}Nota: --solo-verificar no inicia servicios ni cambia configuraciones.${colors.reset}\n`);
    return { exito: false, faltantes };
  }

  console.log(`\n${colors.green}${colors.bold}✔ VERIFICACIÓN COMPLETA EXITOSA (Solo Lectura): Todos los servicios requeridos están activos y son correctos.${colors.reset}\n`);
  return { exito: true, faltantes: [] };
}

// -----------------------------------------------------------------------------
// PUNTO 2: GESTIÓN DE BLOQUEO EXCLUSIVO, MUTEX ATÓMICO Y RECUPERACIÓN SEGURA
// -----------------------------------------------------------------------------
let activeLockPath = LOCK_FILE;
let activeOwnerToken = null;

export function conMutexDeBloqueo(lockPath, fn, {
  fsModule = fs,
  maxWaitMs = 5000,
  retryIntervalMs = 25,
  processAliveFn = null,
  pid = process.pid,
  gracePeriodMs = 2000,
} = {}) {
  const mutexPath = `${lockPath}.mutex`;
  const ownerFile = path.join(mutexPath, 'owner.json');
  const myMutexToken = crypto.randomUUID();
  const myAnchorFile = path.join(mutexPath, `owner_${myMutexToken}.active`);
  const start = Date.now();
  let acquired = false;

  const renameSyncFn = typeof fsModule.renameSync === 'function' ? fsModule.renameSync.bind(fsModule) : fs.renameSync;
  const rmSyncFn = typeof fsModule.rmSync === 'function' ? fsModule.rmSync.bind(fsModule) : (p) => {
    try { fsModule.rmdirSync(p); } catch (_) {}
  };

  while (Date.now() - start < maxWaitMs) {
    try {
      fsModule.mkdirSync(mutexPath);
      // Escribir identidad del propietario y el ancla atómica de pertenencia
      try {
        fsModule.writeFileSync(
          ownerFile,
          JSON.stringify({ mutexToken: myMutexToken, pid, createdAt: Date.now() }),
          'utf-8'
        );
        fsModule.writeFileSync(myAnchorFile, `${myMutexToken}:${pid}`, 'utf-8');
      } catch (_) {}
      acquired = true;
      break;
    } catch (err) {
      if (err.code === 'EEXIST') {
        let existingOwner = null;
        let validOwnerJson = false;

        try {
          if (fsModule.existsSync(ownerFile)) {
            const raw = fsModule.readFileSync(ownerFile, 'utf-8');
            if (raw && raw.trim().length > 0) {
              existingOwner = JSON.parse(raw);
              if (existingOwner && typeof existingOwner.pid === 'number' && existingOwner.mutexToken) {
                validOwnerJson = true;
              }
            }
          }
        } catch (_) {}

        if (validOwnerJson) {
          // Comprobar si el proceso propietario sigue con vida
          let ownerAlive = false;

          if (processAliveFn) {
            try {
              const res = processAliveFn(existingOwner.pid);
              if (res === true) ownerAlive = true;
              else if (res === false) ownerAlive = false;
              else if (res && typeof res === 'object') {
                ownerAlive = !!res.alive;
              }
            } catch (pErr) {
              if (pErr.code === 'ESRCH') ownerAlive = false;
              else ownerAlive = true;
            }
          } else {
            try {
              process.kill(existingOwner.pid, 0);
              ownerAlive = true;
            } catch (pErr) {
              if (pErr.code === 'ESRCH') {
                ownerAlive = false;
              } else {
                ownerAlive = true; // EPERM u otro error: no asumir muerto
              }
            }
          }

          if (ownerAlive) {
            // Propietario confirmado activo o con error de permisos:
            // NUNCA eliminar ni robar por antigüedad (incluso si supera 10s)
          } else {
            // Proceso propietario confirmado inexistente (ESRCH).
            // Para evitar la carrera TOCTOU donde otro proceso ya reemplazó el mutex
            // o compite por recuperarlo: reclamar atómicamente el ancla del dueño anterior
            const deadToken = existingOwner.mutexToken;
            const deadAnchorActive = path.join(mutexPath, `owner_${deadToken}.active`);
            const deadAnchorRetiring = path.join(mutexPath, `owner_${deadToken}.retiring`);

            let claimExitosa = false;
            if (fsModule.existsSync(deadAnchorActive)) {
              try {
                renameSyncFn(deadAnchorActive, deadAnchorRetiring);
                claimExitosa = true;
              } catch (_) {}
            } else {
              // Compatibilidad con mutex legado o prueba unitaria sin ancla explícita
              try {
                renameSyncFn(ownerFile, deadAnchorRetiring);
                claimExitosa = true;
              } catch (_) {}
            }

            if (claimExitosa) {
              // Solo este proceso ganó el derecho exclusivo de retirar el mutex abandonado
              const recDir = `${mutexPath}.recovered-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
              try {
                renameSyncFn(mutexPath, recDir);
                try { rmSyncFn(recDir, { recursive: true, force: true }); } catch (_) {}
              } catch (_) {}
            }
          }
        } else {
          // owner.json ausente, en creación o con metadatos no confirmados:
          // Política estricta: NO borrar por antigüedad ni por metadatos incompletos.
          // Esperar reintentos. Si vence el timeout, se rechazará conservadoramente.
        }

        const waitTill = Date.now() + retryIntervalMs;
        while (Date.now() < waitTill) {}
        continue;
      }
      throw err;
    }
  }

  if (!acquired) {
    throw new Error(
      `Timeout esperando mutex en '${mutexPath}' tras ${maxWaitMs}ms. Se detectó contención o metadatos no confirmados en el mutex. Para resolverlo de forma segura con todos los coordinadores detenidos, elimine manualmente '${mutexPath}'.`
    );
  }

  function liberarMutex() {
    try {
      if (fsModule.existsSync(myAnchorFile)) {
        try { fsModule.unlinkSync(myAnchorFile); } catch (_) {}
        try { fsModule.unlinkSync(ownerFile); } catch (_) {}
        try { fsModule.rmdirSync(mutexPath); } catch (_) {}
      }
    } catch (_) {
      try { rmSyncFn(mutexPath, { recursive: true, force: true }); } catch (_) {}
    }
  }

  let isPromise = false;
  try {
    const result = fn();
    if (result && typeof result.then === 'function') {
      isPromise = true;
      return result.finally(() => {
        liberarMutex();
      });
    }
    return result;
  } finally {
    if (!isPromise) {
      liberarMutex();
    }
  }
}

export async function conMutexDeBloqueoAsync(lockPath, fn, {
  fsModule = fs,
  maxWaitMs = 5000,
  retryIntervalMs = 20,
  processAliveFn = null,
  pid = process.pid,
  gracePeriodMs = 2000,
} = {}) {
  const mutexPath = `${lockPath}.mutex`;
  const ownerFile = path.join(mutexPath, 'owner.json');
  const myMutexToken = crypto.randomUUID();
  const myAnchorFile = path.join(mutexPath, `owner_${myMutexToken}.active`);
  const start = Date.now();
  let acquired = false;

  const renameSyncFn = typeof fsModule.renameSync === 'function' ? fsModule.renameSync.bind(fsModule) : fs.renameSync;
  const rmSyncFn = typeof fsModule.rmSync === 'function' ? fsModule.rmSync.bind(fsModule) : (p) => {
    try { fsModule.rmdirSync(p); } catch (_) {}
  };

  while (Date.now() - start < maxWaitMs) {
    try {
      fsModule.mkdirSync(mutexPath);
      try {
        fsModule.writeFileSync(
          ownerFile,
          JSON.stringify({ mutexToken: myMutexToken, pid, createdAt: Date.now() }),
          'utf-8'
        );
        fsModule.writeFileSync(myAnchorFile, `${myMutexToken}:${pid}`, 'utf-8');
      } catch (_) {}
      acquired = true;
      break;
    } catch (err) {
      if (err.code === 'EEXIST') {
        let existingOwner = null;
        let validOwnerJson = false;

        try {
          if (fsModule.existsSync(ownerFile)) {
            const raw = fsModule.readFileSync(ownerFile, 'utf-8');
            if (raw && raw.trim().length > 0) {
              existingOwner = JSON.parse(raw);
              if (existingOwner && typeof existingOwner.pid === 'number' && existingOwner.mutexToken) {
                validOwnerJson = true;
              }
            }
          }
        } catch (_) {}

        if (validOwnerJson) {
          let ownerAlive = false;

          if (processAliveFn) {
            try {
              const res = processAliveFn(existingOwner.pid);
              if (res === true) ownerAlive = true;
              else if (res === false) ownerAlive = false;
              else if (res && typeof res === 'object') {
                ownerAlive = !!res.alive;
              }
            } catch (pErr) {
              if (pErr.code === 'ESRCH') ownerAlive = false;
              else ownerAlive = true;
            }
          } else {
            try {
              process.kill(existingOwner.pid, 0);
              ownerAlive = true;
            } catch (pErr) {
              if (pErr.code === 'ESRCH') {
                ownerAlive = false;
              } else {
                ownerAlive = true;
              }
            }
          }

          if (ownerAlive) {
            // Activo: no tocar
          } else {
            const deadToken = existingOwner.mutexToken;
            const deadAnchorActive = path.join(mutexPath, `owner_${deadToken}.active`);
            const deadAnchorRetiring = path.join(mutexPath, `owner_${deadToken}.retiring`);

            let claimExitosa = false;
            if (fsModule.existsSync(deadAnchorActive)) {
              try {
                renameSyncFn(deadAnchorActive, deadAnchorRetiring);
                claimExitosa = true;
              } catch (_) {}
            } else {
              // Compatibilidad con mutex legado o prueba unitaria sin ancla explícita
              try {
                renameSyncFn(ownerFile, deadAnchorRetiring);
                claimExitosa = true;
              } catch (_) {}
            }

            if (claimExitosa) {
              const recDir = `${mutexPath}.recovered-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
              try {
                renameSyncFn(mutexPath, recDir);
                try { rmSyncFn(recDir, { recursive: true, force: true }); } catch (_) {}
              } catch (_) {}
            }
          }
        } else {
          // Incompleto: no borrar por antigüedad
        }

        await sleep(retryIntervalMs);
        continue;
      }
      throw err;
    }
  }

  if (!acquired) {
    throw new Error(
      `Timeout esperando mutex en '${mutexPath}' tras ${maxWaitMs}ms. Se detectó contención o metadatos no confirmados en el mutex. Para resolverlo de forma segura con todos los coordinadores detenidos, elimine manualmente '${mutexPath}'.`
    );
  }

  function liberarMutex() {
    try {
      if (fsModule.existsSync(myAnchorFile)) {
        try { fsModule.unlinkSync(myAnchorFile); } catch (_) {}
        try { fsModule.unlinkSync(ownerFile); } catch (_) {}
        try { fsModule.rmdirSync(mutexPath); } catch (_) {}
      }
    } catch (_) {
      try { rmSyncFn(mutexPath, { recursive: true, force: true }); } catch (_) {}
    }
  }

  try {
    return await fn();
  } finally {
    liberarMutex();
  }
}

export function evaluarYRecuperarBloqueo({
  lockPath,
  token,
  lockPayload,
  fsModule,
  processAliveFn,
  gracePeriodMs = 2000,
}) {
  if (!fsModule.existsSync(lockPath)) {
    try {
      fsModule.writeFileSync(lockPath, lockPayload, { flag: 'wx', encoding: 'utf-8' });
      return { adquirido: true, ownerToken: token, lockPath };
    } catch (err) {
      if (err.code !== 'EEXIST') {
        return { adquirido: false, ownerToken: null, razon: `Error al escribir bloqueo: ${err.message}` };
      }
    }
  }

  let stat = null;
  let raw = '';
  try {
    stat = fsModule.statSync(lockPath);
    raw = fsModule.readFileSync(lockPath, 'utf-8');
  } catch (err) {
    if (err.code === 'ENOENT') {
      try {
        fsModule.writeFileSync(lockPath, lockPayload, { flag: 'wx', encoding: 'utf-8' });
        return { adquirido: true, ownerToken: token, lockPath };
      } catch (_) { }
    }
    return { adquirido: false, ownerToken: null, razon: `Error al leer archivo de bloqueo: ${err.message}` };
  }

  // Comprobar archivo incompleto / en proceso de escritura
  let existingLock = null;
  let esValidoJson = false;
  try {
    if (raw && raw.trim().length > 0) {
      existingLock = JSON.parse(raw);
      if (existingLock && typeof existingLock.pid === 'number') {
        esValidoJson = true;
      }
    }
  } catch (_) { }

  const mtimeMs = stat ? stat.mtimeMs : Date.now();
  const fileAgeMs = Date.now() - mtimeMs;

  if (!esValidoJson) {
    // Si el archivo está incompleto o vacío pero fue modificado dentro del margen razonable (periodo de gracia)
    if (fileAgeMs < gracePeriodMs) {
      return {
        adquirido: false,
        ownerToken: null,
        razon: 'Archivo de bloqueo en proceso de creación (incompleto, dentro del periodo de gracia).',
      };
    }
    // Si ya superó el periodo de gracia y sigue corrupto, es un bloqueo abandonado
    try {
      fsModule.unlinkSync(lockPath);
      fsModule.writeFileSync(lockPath, lockPayload, { flag: 'wx', encoding: 'utf-8' });
      return { adquirido: true, ownerToken: token, lockPath };
    } catch (e) {
      return { adquirido: false, ownerToken: null, razon: `Fallo al recuperar bloqueo corrupto: ${e.message}` };
    }
  }

  // Si ya somos los propietarios legítimos con el mismo token
  if (existingLock.ownerToken === token) {
    return { adquirido: true, ownerToken: token, lockPath };
  }

  // Evaluar vida de un PID
  function checkPid(targetPid) {
    if (processAliveFn) {
      try {
        const res = processAliveFn(targetPid);
        if (res === true) return { alive: true, error: false };
        if (res === false) return { alive: false, error: false };
        if (res && typeof res === 'object') return { alive: !!res.alive, error: !!res.error };
        return { alive: false, error: false };
      } catch (err) {
        if (err.code === 'ESRCH') return { alive: false, error: false };
        return { alive: true, error: true };
      }
    }
    try {
      process.kill(targetPid, 0);
      return { alive: true, error: false };
    } catch (err) {
      if (err.code === 'ESRCH') return { alive: false, error: false };
      return { alive: true, error: true };
    }
  }

  // 1. Evaluar vida del coordinador principal
  const coordStatus = checkPid(existingLock.pid);
  if (coordStatus.alive) {
    return {
      adquirido: false,
      ownerToken: null,
      propietario: existingLock,
      razon: coordStatus.error
        ? `No se pudo determinar con certeza el estado del PID ${existingLock.pid} (error de permisos). Se asume activo por seguridad.`
        : `Ya existe una instancia activa del coordinador en ejecución (PID ${existingLock.pid}).`,
    };
  }

  // 2. Evaluar PIDs dependientes registrados (de una limpieza incompleta previa)
  const dependentPids = Array.isArray(existingLock.dependentPids) ? existingLock.dependentPids : [];
  const liveDependent = [];
  for (const depPid of dependentPids) {
    const depStatus = checkPid(depPid);
    if (depStatus.alive) {
      liveDependent.push(depPid);
    }
  }

  if (liveDependent.length > 0) {
    return {
      adquirido: false,
      ownerToken: null,
      propietario: existingLock,
      razon: `El coordinador previo no está activo, pero existen procesos dependientes aún en ejecución (PIDs: [${liveDependent.join(', ')}]). No se recupera el bloqueo para evitar duplicación de servicios.`,
    };
  }

  // Proceso confirmado muerto (ESRCH) y sin procesos dependientes activos -> Bloqueo abandonado legítimo
  try {
    fsModule.unlinkSync(lockPath);
    fsModule.writeFileSync(lockPath, lockPayload, { flag: 'wx', encoding: 'utf-8' });
    return { adquirido: true, ownerToken: token, lockPath };
  } catch (err) {
    return {
      adquirido: false,
      ownerToken: null,
      razon: `Error al sobrescribir bloqueo abandonado: ${err.message}`,
    };
  }
}

export function adquirirBloqueoCoordinador({
  lockPath = LOCK_FILE,
  ownerToken = null,
  pid = process.pid,
  workspaceSig = WORKSPACE_SIGNATURE,
  fsModule = fs,
  processAliveFn = null,
  gracePeriodMs = 2000,
  maxWaitMs = 5000,
} = {}) {
  const token = ownerToken || crypto.randomUUID();
  const lockPayload = JSON.stringify({
    ownerToken: token,
    pid,
    createdAt: Date.now(),
    workspaceSignature: workspaceSig,
  });

  // Toda inspección y adquisición se unifica estrictamente bajo el protocolo de mutex
  return conMutexDeBloqueo(
    lockPath,
    () => {
      return evaluarYRecuperarBloqueo({
        lockPath,
        token,
        lockPayload,
        fsModule,
        processAliveFn,
        gracePeriodMs,
      });
    },
    { fsModule, maxWaitMs, processAliveFn, pid, gracePeriodMs }
  );
}

export async function adquirirBloqueoCoordinadorAsync({
  lockPath = LOCK_FILE,
  ownerToken = null,
  pid = process.pid,
  workspaceSig = WORKSPACE_SIGNATURE,
  fsModule = fs,
  processAliveFn = null,
  gracePeriodMs = 2000,
  maxWaitMs = 5000,
} = {}) {
  const token = ownerToken || crypto.randomUUID();
  const lockPayload = JSON.stringify({
    ownerToken: token,
    pid,
    createdAt: Date.now(),
    workspaceSignature: workspaceSig,
  });

  // Toda inspección y adquisición asíncrona unificada bajo el mutex
  return conMutexDeBloqueoAsync(
    lockPath,
    async () => {
      return evaluarYRecuperarBloqueo({
        lockPath,
        token,
        lockPayload,
        fsModule,
        processAliveFn,
        gracePeriodMs,
      });
    },
    { fsModule, maxWaitMs, processAliveFn, pid, gracePeriodMs }
  );
}

export function actualizarBloqueoCoordinador({
  lockPath = LOCK_FILE,
  ownerToken = null,
  dependentPids = [],
  pendingProcesses = [],
  fsModule = fs,
} = {}) {
  if (!ownerToken || !fsModule.existsSync(lockPath)) return false;

  return conMutexDeBloqueo(
    lockPath,
    () => {
      if (!fsModule.existsSync(lockPath)) return false;
      try {
        const raw = fsModule.readFileSync(lockPath, 'utf-8');
        let lockObj = null;
        try { lockObj = JSON.parse(raw); } catch (_) {}
        if (!lockObj || lockObj.ownerToken !== ownerToken) {
          return false;
        }

        const mergedDependent = Array.from(new Set([...(lockObj.dependentPids || []), ...dependentPids]));
        const updated = {
          ...lockObj,
          dependentPids: mergedDependent,
          pendingProcesses: pendingProcesses.length > 0 ? pendingProcesses : (lockObj.pendingProcesses || []),
          updatedAt: Date.now(),
        };

        fsModule.writeFileSync(lockPath, JSON.stringify(updated, null, 2), 'utf-8');
        return true;
      } catch (_) {
        return false;
      }
    },
    { fsModule }
  );
}

export function liberarBloqueoCoordinador({
  lockPath = LOCK_FILE,
  ownerToken = null,
  fsModule = fs,
} = {}) {
  if (!ownerToken) {
    return { liberado: false, estado: 'sin_token', razon: 'No se especificó un token de propietario válido.' };
  }
  if (!fsModule.existsSync(lockPath)) {
    return { liberado: true, estado: 'ya_ausente', yaAusente: true, razon: 'El archivo de bloqueo ya no existe.' };
  }

  return conMutexDeBloqueo(
    lockPath,
    () => {
      if (!fsModule.existsSync(lockPath)) {
        return { liberado: true, estado: 'ya_ausente', yaAusente: true, razon: 'El archivo de bloqueo ya no existe.' };
      }
      try {
        const raw = fsModule.readFileSync(lockPath, 'utf-8');
        let lockObj = null;
        try {
          lockObj = JSON.parse(raw);
        } catch (_) {}

        if (!lockObj || lockObj.ownerToken !== ownerToken) {
          return {
            liberado: false,
            estado: 'bloqueo_ajeno',
            razon: `El archivo de bloqueo pertenece a otra sesión o el token no coincide (token actual: ${lockObj?.ownerToken ? lockObj.ownerToken.slice(0, 8) + '...' : 'inválido'}, token solicitado: ${ownerToken.slice(0, 8)}...).`,
          };
        }

        fsModule.unlinkSync(lockPath);
        return { liberado: true, estado: 'liberado', razon: 'Archivo de bloqueo eliminado exitosamente.' };
      } catch (err) {
        return {
          liberado: false,
          estado: 'error_acceso',
          razon: `Error de E/S o permisos al acceder al archivo de bloqueo: ${err.message}`,
        };
      }
    },
    { fsModule }
  );
}

// -----------------------------------------------------------------------------
// GESTOR DE PROCESOS PROPIOS, DESCENDIENTES Y LIMPIEZA RIGUROSA
// -----------------------------------------------------------------------------
let startedBackendRecord = null;
let startedFrontendRecord = null;

let startedBackendProcess = null;
let startedBackendPid = null;
let startedFrontendProcess = null;
let startedFrontendPid = null;

let backendReused = false;
let frontendReused = false;
let isShuttingDown = false;

export function setStartedBackendRecord(record) {
  startedBackendRecord = record;
}

export function setStartedFrontendRecord(record) {
  startedFrontendRecord = record;
}

export function setActiveLockSession(lockPath, ownerToken) {
  activeLockPath = lockPath;
  activeOwnerToken = ownerToken;
}

export function descubrirDescendientes(rootPid) {
  if (!rootPid || typeof rootPid !== 'number') return [];
  const script = `$procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Select-Object ProcessId, ParentProcessId, CommandLine; if ($procs) { $procs | ConvertTo-Json -Compress }`;
  const res = runCmd(`powershell -NoProfile -Command "${script}"`);
  if (!res.success || !res.stdout) return [];
  let procList = [];
  try {
    const parsed = JSON.parse(res.stdout);
    procList = Array.isArray(parsed) ? parsed : [parsed];
  } catch (_) {
    return [];
  }

  const descendants = [];
  const pidsToScan = [rootPid];
  const visited = new Set([rootPid]);

  while (pidsToScan.length > 0) {
    const currentParent = pidsToScan.shift();
    const children = procList.filter((p) => p && p.ParentProcessId === currentParent);
    for (const ch of children) {
      if (ch && ch.ProcessId && !visited.has(ch.ProcessId)) {
        visited.add(ch.ProcessId);
        descendants.push({
          pid: ch.ProcessId,
          parentPid: ch.ParentProcessId,
          cmdLine: ch.CommandLine || '',
        });
        pidsToScan.push(ch.ProcessId);
      }
    }
  }
  return descendants;
}

export function terminarArbolProcesosPropio(record, {
  logFn = console.log,
  warnFn = console.warn,
  timeoutMs = 6000,
  queryProcessFn = null,
  killPidFn = null,
  processAliveFn = null,
} = {}) {
  if (!record || !record.wrapperPid) {
    return {
      exito: true,
      pidsTerminados: [],
      pidsVivos: [],
      pidsDesconocidos: [],
      pidsRechazados: [],
    };
  }

  const wrapperPid = record.wrapperPid;
  const pidsValidadosParaMatar = [];
  const pidsDesconocidos = [];
  const pidsRechazados = [];

  // Helper de comprobación de vida
  function isPidAlive(pid) {
    if (processAliveFn) {
      try {
        const res = processAliveFn(pid);
        if (res === true) return { alive: true };
        if (res === false) return { alive: false };
        if (res && typeof res === 'object') return { alive: !!res.alive, error: res.error || null };
        return { alive: false };
      } catch (err) {
        if (err.code === 'ESRCH') return { alive: false };
        return { alive: true, error: err };
      }
    }
    try {
      process.kill(pid, 0);
      return { alive: true };
    } catch (err) {
      if (err.code === 'ESRCH') return { alive: false };
      return { alive: true, error: err };
    }
  }

  // Helper de consulta Win32_Process
  function queryProc(pid) {
    if (queryProcessFn) {
      try {
        return queryProcessFn(pid);
      } catch (err) {
        return { success: false, error: err };
      }
    }
    try {
      const script = `$p = Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}' -ErrorAction SilentlyContinue; if ($p) { [PSCustomObject]@{ ProcessId = $p.ProcessId; ParentProcessId = $p.ParentProcessId; CommandLine = $p.CommandLine } | ConvertTo-Json -Compress }`;
      const res = runCmd(`powershell -NoProfile -Command "${script}"`);
      if (!res.success || !res.stdout || !res.stdout.trim()) {
        return { success: false, error: new Error(res.stderr || 'No output') };
      }
      const data = JSON.parse(res.stdout);
      return { success: true, data };
    } catch (err) {
      return { success: false, error: err };
    }
  }

  // 1. Recopilar candidatos en el árbol y knownPids
  const candidateList = [];
  const descendants = descubrirDescendientes(wrapperPid);
  for (const desc of descendants) {
    if (desc && desc.pid && desc.pid !== wrapperPid) {
      candidateList.push({ pid: desc.pid, parentPid: desc.parentPid, isWrapper: false });
    }
  }

  if (record.knownPids) {
    for (const kp of record.knownPids) {
      if (kp && kp !== wrapperPid && !candidateList.some((c) => c.pid === kp)) {
        candidateList.push({ pid: kp, parentPid: null, isWrapper: false });
      }
    }
  }

  candidateList.push({ pid: wrapperPid, parentPid: null, isWrapper: true });

  // 2. Validar cada candidato individualmente
  for (const cand of candidateList) {
    const pid = cand.pid;
    const liveness = isPidAlive(pid);
    if (!liveness.alive) {
      continue;
    }
    if (liveness.error) {
      warnFn(`  [ADVERTENCIA] Error de consulta de vida para PID ${pid}. Tratado como desconocido.`);
      pidsDesconocidos.push(pid);
      continue;
    }

    const q = queryProc(pid);
    if (!q.success || !q.data) {
      warnFn(`  [ADVERTENCIA] No se pudo consultar Win32_Process para PID ${pid}. No se terminará por seguridad (desconocido).`);
      pidsDesconocidos.push(pid);
      continue;
    }

    const cmdLine = (q.data.CommandLine || '').trim();
    if (!cmdLine) {
      warnFn(`  [ADVERTENCIA] CommandLine vacío para PID ${pid}. No se terminará por seguridad (desconocido).`);
      pidsDesconocidos.push(pid);
      continue;
    }

    if (cand.isWrapper) {
      if (record.wrapperCmdPattern) {
        const matches = typeof record.wrapperCmdPattern === 'function'
          ? record.wrapperCmdPattern(cmdLine)
          : cmdLine.toLowerCase().includes(String(record.wrapperCmdPattern).toLowerCase());
        if (!matches) {
          warnFn(`  [ADVERTENCIA] PID envoltorio ${pid} rechazado: CommandLine no coincide con el patrón esperado.`);
          pidsRechazados.push(pid);
          continue;
        }
      }
      pidsValidadosParaMatar.push(pid);
    } else {
      if (record.childCmdPattern) {
        const matches = typeof record.childCmdPattern === 'function'
          ? record.childCmdPattern(cmdLine)
          : cmdLine.toLowerCase().includes(String(record.childCmdPattern).toLowerCase());
        if (!matches) {
          warnFn(`  [ADVERTENCIA] PID hijo ${pid} rechazado: CommandLine no coincide con el patrón esperado.`);
          pidsRechazados.push(pid);
          continue;
        }
      }
      pidsValidadosParaMatar.push(pid);
    }
  }

  // 3. Terminar individualmente cada proceso validado (NO USAR /T)
  const pidsHijos = pidsValidadosParaMatar.filter((p) => p !== wrapperPid);
  const pidsOrdenados = [...pidsHijos, ...(pidsValidadosParaMatar.includes(wrapperPid) ? [wrapperPid] : [])];

  for (const pid of pidsOrdenados) {
    if (killPidFn) {
      try { killPidFn(pid); } catch (_) {}
    } else {
      try {
        execSync(`taskkill /pid ${pid} /F`, { stdio: 'ignore', windowsHide: true });
      } catch (_) {}
    }
  }

  if (record.wrapperProcess && typeof record.wrapperProcess.kill === 'function' && pidsValidadosParaMatar.includes(wrapperPid)) {
    try {
      record.wrapperProcess.kill('SIGTERM');
    } catch (_) {}
  }

  // 4. Sondeo y confirmación real de terminación
  const pollStart = Date.now();
  let remainingPids = [...pidsValidadosParaMatar];

  while (Date.now() - pollStart < timeoutMs && remainingPids.length > 0) {
    const stillAlive = [];
    for (const pid of remainingPids) {
      const live = isPidAlive(pid);
      if (live.alive) {
        stillAlive.push(pid);
      }
    }
    remainingPids = stillAlive;
    if (remainingPids.length === 0) break;
    const waitTill = Date.now() + 50;
    while (Date.now() < waitTill) {}
  }

  if (remainingPids.length > 0) {
    for (const pid of remainingPids) {
      if (killPidFn) {
        try { killPidFn(pid); } catch (_) {}
      } else {
        try {
          execSync(`taskkill /pid ${pid} /F`, { stdio: 'ignore', windowsHide: true });
        } catch (_) {}
      }
    }
    const finalStill = [];
    for (const pid of remainingPids) {
      const live = isPidAlive(pid);
      if (live.alive) {
        finalStill.push(pid);
      }
    }
    remainingPids = finalStill;
  }

  const deadPids = pidsValidadosParaMatar.filter((p) => !remainingPids.includes(p));
  const allDead = remainingPids.length === 0 && pidsDesconocidos.length === 0;

  return {
    exito: allDead,
    pidsTerminados: deadPids,
    pidsVivos: remainingPids,
    pidsDesconocidos,
    pidsRechazados,
  };
}

export function terminarProcesoPropio(childProcess, pid, cmdSubstr) {
  if (!pid) return;
  const mockRecord = {
    wrapperPid: pid,
    wrapperProcess: childProcess,
    wrapperCmdPattern: cmdSubstr || '',
    childCmdPattern: cmdSubstr || '',
    knownPids: new Set([pid]),
  };
  terminarArbolProcesosPropio(mockRecord);
}

export function getStartedBackendRecord() {
  return startedBackendRecord;
}

export function getStartedFrontendRecord() {
  return startedFrontendRecord;
}

export function getActiveOwnerToken() {
  return activeOwnerToken;
}

export function getActiveLockPath() {
  return activeLockPath;
}

export async function ejecutarLimpieza({
  code = 0,
  exitOnFinish = false,
  logFn = console.log,
  warnFn = console.warn,
  errorFn = console.error,
  timeoutMs = 6000,
  terminarArbolFn = terminarArbolProcesosPropio,
  liberarBloqueoFn = liberarBloqueoCoordinador,
  actualizarBloqueoFn = actualizarBloqueoCoordinador,
} = {}) {
  logFn(`\n${colors.cyan}Cerrando sesión de desarrollo Lysandri Executive...${colors.reset}`);

  const pidsTerminadosTotal = [];
  const pidsVivosTotal = [];
  const pidsDesconocidosTotal = [];
  const pidsRechazadosTotal = [];

  // 1. Detener árbol de Frontend Vite (si fue iniciado por esta sesión)
  if (startedFrontendRecord && startedFrontendRecord.wrapperPid) {
    logFn(`  ${colors.gray}Deteniendo proceso Vite iniciado (PID ${startedFrontendRecord.wrapperPid})...${colors.reset}`);
    const rFront = terminarArbolFn(startedFrontendRecord, { logFn, warnFn, timeoutMs });
    pidsTerminadosTotal.push(...rFront.pidsTerminados);
    pidsVivosTotal.push(...rFront.pidsVivos);
    if (rFront.pidsDesconocidos) pidsDesconocidosTotal.push(...rFront.pidsDesconocidos);
    if (rFront.pidsRechazados) pidsRechazadosTotal.push(...rFront.pidsRechazados);

    const pendientesFront = [...rFront.pidsVivos, ...(rFront.pidsDesconocidos || [])];
    if (pendientesFront.length > 0) {
      // Conservar el record para permitir reintento
      startedFrontendRecord = {
        ...startedFrontendRecord,
        knownPids: new Set(pendientesFront),
      };
    } else {
      startedFrontendRecord = null;
      startedFrontendProcess = null;
      startedFrontendPid = null;
    }
  }

  // 2. Detener árbol de Backend (si fue iniciado por esta sesión)
  if (startedBackendRecord && startedBackendRecord.wrapperPid) {
    logFn(`  ${colors.gray}Deteniendo proceso Backend iniciado y descendientes (PID ${startedBackendRecord.wrapperPid})...${colors.reset}`);
    const rBack = terminarArbolFn(startedBackendRecord, { logFn, warnFn, timeoutMs });
    pidsTerminadosTotal.push(...rBack.pidsTerminados);
    pidsVivosTotal.push(...rBack.pidsVivos);
    if (rBack.pidsDesconocidos) pidsDesconocidosTotal.push(...rBack.pidsDesconocidos);
    if (rBack.pidsRechazados) pidsRechazadosTotal.push(...rBack.pidsRechazados);

    const pendientesBack = [...rBack.pidsVivos, ...(rBack.pidsDesconocidos || [])];
    if (pendientesBack.length > 0) {
      // Conservar el record para permitir reintento
      startedBackendRecord = {
        ...startedBackendRecord,
        knownPids: new Set(pendientesBack),
      };
    } else {
      startedBackendRecord = null;
      startedBackendProcess = null;
      startedBackendPid = null;
    }
  }

  // 3. Evaluar si algún proceso propio quedó pendiente (vivo o desconocido)
  const pidsPendientesTotal = [...pidsVivosTotal, ...pidsDesconocidosTotal];
  const exitoTerminacion = pidsPendientesTotal.length === 0;

  if (!exitoTerminacion) {
    errorFn(`  ${colors.red}✘ ALERTA: Limpieza incompleta. Procesos pendientes: [${pidsPendientesTotal.join(', ')}]. No se declara éxito ni se libera el bloqueo.${colors.reset}`);
  }

  // 4. Liberar bloqueo exclusivo al final de la limpieza ÚNICAMENTE si la terminación fue completa
  const teniaBloqueo = Boolean(activeOwnerToken);
  let bloqueoLiberado = false;
  let bloqueoActualizado = false;
  let estadoLiberacion = teniaBloqueo ? 'pendiente' : 'no_requerido';

  if (exitoTerminacion) {
    if (teniaBloqueo) {
      const resLiberacion = liberarBloqueoFn({ lockPath: activeLockPath, ownerToken: activeOwnerToken });
      const fueLiberado = typeof resLiberacion === 'object' && resLiberacion !== null
        ? Boolean(resLiberacion.liberado)
        : Boolean(resLiberacion);
      estadoLiberacion = typeof resLiberacion === 'object' && resLiberacion !== null
        ? (resLiberacion.estado || (fueLiberado ? 'liberado' : 'error_acceso'))
        : (fueLiberado ? 'liberado' : 'error_acceso');

      if (fueLiberado) {
        bloqueoLiberado = true;
        activeOwnerToken = null;
        logFn(`  ${colors.green}✔ Bloqueo exclusivo de sesión liberado correctamente (${estadoLiberacion}).${colors.reset}`);
      } else {
        bloqueoLiberado = false;
        // CONSERVAR activeOwnerToken para reintentos posteriores
        const razon = typeof resLiberacion === 'object' && resLiberacion?.razon ? resLiberacion.razon : 'Fallo en la operación de liberación';
        errorFn(`  ${colors.red}✘ ALERTA: No se pudo liberar el bloqueo exclusivo [Estado: ${estadoLiberacion}]. ${razon}. Se conserva el token para reintento.${colors.reset}`);
      }
    } else {
      bloqueoLiberado = true;
    }
  } else {
    // Si la limpieza quedó incompleta:
    // Conservar activeOwnerToken y actualizar el bloqueo con los PIDs pendientes
    if (teniaBloqueo) {
      bloqueoActualizado = actualizarBloqueoFn({
        lockPath: activeLockPath,
        ownerToken: activeOwnerToken,
        dependentPids: pidsPendientesTotal,
      });
    }
  }

  // Éxito global requiere: terminación de procesos completa Y (no tener bloqueo O haberlo liberado)
  const exitoGlobal = exitoTerminacion && (!teniaBloqueo || bloqueoLiberado);

  const serviciosReusados = [];
  serviciosReusados.push('Docker Desktop');
  serviciosReusados.push('PostgreSQL Clone (127.0.0.1:5434)');
  if (backendReused) serviciosReusados.push('Backend local (reutilizado)');
  if (frontendReused) serviciosReusados.push('Frontend Vite (reutilizado)');

  logFn(`  ${colors.green}✔ Servicios preservados: ${serviciosReusados.join(', ')}.${colors.reset}`);

  if (exitoGlobal) {
    logFn(`${colors.cyan}Entorno cerrado correctamente.${colors.reset}\n`);
  } else {
    const motivoIncompleto = !exitoTerminacion && !bloqueoLiberado
      ? 'procesos pendientes y bloqueo no liberado'
      : !exitoTerminacion
        ? 'procesos pendientes'
        : 'bloqueo de sesión no liberado';
    warnFn(`${colors.yellow}Entorno cerrado con advertencias (cierre incompleto: ${motivoIncompleto}).${colors.reset}\n`);
  }

  if (exitOnFinish) {
    const exitCode = exitoGlobal ? (code === 0 ? 0 : code) : (code !== 0 ? code : 1);
    process.exit(exitCode);
  }

  return {
    exito: exitoGlobal,
    exitoTerminacion,
    bloqueoLiberado,
    bloqueoActualizado,
    estadoLiberacion,
    activeOwnerToken,
    pidsTerminados: pidsTerminadosTotal,
    pidsVivos: pidsVivosTotal,
    pidsDesconocidos: pidsDesconocidosTotal,
    pidsRechazados: pidsRechazadosTotal,
  };
}

export function cleanupAndExit(code = 0) {
  if (isShuttingDown) return;
  isShuttingDown = true;
  ejecutarLimpieza({ code, exitOnFinish: true }).catch(() => {
    process.exit(code !== 0 ? code : 1);
  });
}

// -----------------------------------------------------------------------------
// COORDINADOR PRINCIPAL (INICIO Y REUTILIZACIÓN)
// -----------------------------------------------------------------------------
export async function iniciarEntorno({
  permitirJarDesactualizado = false,
  noAbrirNavegador = false,
  lockPath = LOCK_FILE,
} = {}) {
  console.log(`\n${colors.cyan}${colors.bold}================================================================================${colors.reset}`);
  console.log(`${colors.cyan}${colors.bold}COORDINADOR DE INICIO ÚNICO: LYSANDRI EXECUTIVE DEV ENVIRONMENT${colors.reset}`);
  console.log(`${colors.cyan}${colors.bold}================================================================================${colors.reset}\n`);

  // Adquisición atómica y exclusiva del bloqueo
  activeLockPath = lockPath;
  const lockResult = adquirirBloqueoCoordinador({ lockPath });
  if (!lockResult.adquirido) {
    console.log(`  ${colors.yellow}${lockResult.razon} Se evita duplicar ejecución.${colors.reset}\n`);
    process.exit(0);
  }
  activeOwnerToken = lockResult.ownerToken;

  // 1. Asegurar Docker Desktop
  logStep('1/6', 'Comprobando Docker Desktop...');
  const checkDocker = () => {
    const res = runCmd('docker version --format "{{.Server.Os}}"');
    return res.success && res.stdout.toLowerCase().includes('linux');
  };

  if (!checkDocker()) {
    logInfo('El motor Docker no responde. Localizando ejecutable de Docker Desktop...');
    const candidates = [
      'C:\\Users\\danny\\AppData\\Local\\Programs\\DockerDesktop\\Docker Desktop.exe',
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'DockerDesktop', 'Docker Desktop.exe'),
      path.join(process.env.ProgramFiles || '', 'Docker', 'Docker', 'Docker Desktop.exe'),
      'C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe',
    ];
    let dockerDesktopPath = candidates.find((c) => fs.existsSync(c));
    if (!dockerDesktopPath) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] Docker Desktop no está instalado o no se encontró el ejecutable.${colors.reset}\n`);
      cleanupAndExit(1);
    }

    logInfo(`Iniciando Docker Desktop desde: ${dockerDesktopPath}...`);
    try {
      const child = spawn(dockerDesktopPath, [], { detached: true, stdio: 'ignore', windowsHide: true });
      child.unref();
    } catch (err) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] No se pudo iniciar Docker Desktop: ${err.message}${colors.reset}\n`);
      cleanupAndExit(1);
    }

    const timeoutSeconds = 90;
    const start = Date.now();
    let ready = false;
    while ((Date.now() - start) / 1000 < timeoutSeconds) {
      await sleep(2500);
      const elapsed = Math.round((Date.now() - start) / 1000);
      process.stdout.write(`  ${colors.gray}Esperando respuesta del motor Docker (${elapsed}s / ${timeoutSeconds}s)...${colors.reset}\r`);
      if (checkDocker()) {
        ready = true;
        break;
      }
    }
    process.stdout.write('\n');
    if (!ready) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] Docker Desktop no respondió tras 90 segundos.${colors.reset}\n`);
      cleanupAndExit(1);
    }
  }
  logOk('Motor Docker Linux disponible.');

  // 2. Asegurar Contenedor lysandri-postgres-clone
  logStep('2/6', 'Iniciando y verificando contenedor lysandri-postgres-clone...');
  const inspectRes = runCmd('docker inspect lysandri-postgres-clone');
  if (!inspectRes.success || !inspectRes.stdout) {
    console.error(`\n${colors.red}${colors.bold}[FATAL] El contenedor 'lysandri-postgres-clone' no existe. Por restricciones de seguridad, no se crean contenedores automáticamente.${colors.reset}\n`);
    cleanupAndExit(1);
  }

  let inspectObj;
  try {
    const parsed = JSON.parse(inspectRes.stdout);
    inspectObj = Array.isArray(parsed) ? parsed[0] : parsed;
  } catch (e) {
    console.error(`\n${colors.red}${colors.bold}[FATAL] Error interpretando configuración de docker inspect: ${e.message}${colors.reset}\n`);
    cleanupAndExit(1);
  }

  // Validar configuración de puertos inicial
  const valMapeoInicial = validarMapeoPuertosClon(inspectObj);
  if (!valMapeoInicial.valido) {
    console.error(`\n${colors.red}${colors.bold}[FATAL] Mapeo de puertos inválido en el contenedor: ${valMapeoInicial.razon}${colors.reset}\n`);
    cleanupAndExit(1);
  }

  // Si está detenido, iniciarlo
  if (inspectObj.State?.Status !== 'running') {
    logInfo("El contenedor 'lysandri-postgres-clone' está detenido. Iniciando...");
    const startRes = runCmd('docker start lysandri-postgres-clone');
    if (!startRes.success) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] No se pudo iniciar el contenedor 'lysandri-postgres-clone': ${startRes.stderr}${colors.reset}\n`);
      cleanupAndExit(1);
    }

    let running = false;
    for (let i = 0; i < 15; i++) {
      await sleep(1000);
      const s = runCmd('docker inspect lysandri-postgres-clone --format "{{.State.Status}}"');
      if (s.success && s.stdout === 'running') {
        running = true;
        break;
      }
    }
    if (!running) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] El contenedor 'lysandri-postgres-clone' no alcanzó el estado 'running'.${colors.reset}\n`);
      cleanupAndExit(1);
    }
    logOk("Contenedor 'lysandri-postgres-clone' iniciado correctamente.");

    // Re-validar puertos en ejecución
    const inspectRunning = runCmd('docker inspect lysandri-postgres-clone');
    if (inspectRunning.success) {
      const parsedR = JSON.parse(inspectRunning.stdout);
      const valRunning = validarMapeoPuertosClon(Array.isArray(parsedR) ? parsedR[0] : parsedR);
      if (!valRunning.valido) {
        console.error(`\n${colors.red}${colors.bold}[FATAL] Verificación de puertos en ejecución falló: ${valRunning.razon}${colors.reset}\n`);
        cleanupAndExit(1);
      }
    }
  } else {
    logOk("Contenedor 'lysandri-postgres-clone' ya se encontraba en ejecución.");
  }

  // 3. Esperar disponibilidad real y autenticación de PostgreSQL (Punto 1)
  logStep('3/6', 'Esperando disponibilidad y autenticación de PostgreSQL en 127.0.0.1:5434...');
  const pgResult = await esperarDisponibilidadPostgres({
    cloneHost: '127.0.0.1',
    clonePort: 5434,
    cloneDatabase: 'lysandri_clone_db',
    timeoutMs: 45000,
    onProgress: (elapsed, total) => {
      process.stdout.write(`  ${colors.gray}Verificando PostgreSQL (${elapsed}s / ${total}s)...${colors.reset}\r`);
    },
  });
  process.stdout.write('\n');

  if (!pgResult.listo) {
    console.error(`\n${colors.red}${colors.bold}[FATAL] ${pgResult.mensaje}${colors.reset}\n`);
    cleanupAndExit(1);
  }
  logOk('Conexión TCP e identidades autenticadas (lysandri_app, lysandri_admin) confirmadas en 127.0.0.1:5434.');

  // 4. Asegurar Backend
  logStep('4/6', 'Comprobando e iniciando backend local en puerto 8080...');
  let estadoJar = comprobarJarActualizado();

  const backendIdent = await verificarIdentidadBackend({ port: 8080 });
  if (backendIdent.activo) {
    if (backendIdent.esNuestro) {
      logOk(`Backend ya activo y verificado (PID ${backendIdent.pid}, perfil 'clone', base 127.0.0.1:5434/lysandri_clone_db). Se reutiliza.`);
      backendReused = true;
      if (estadoJar.desactualizado) {
        logWarn(`Existen modificaciones en el código fuente posteriores al JAR (${path.basename(estadoJar.archivo)}). Recompile cuando desee aplicar cambios.`);
      }
    } else {
      console.error(`\n${colors.red}${colors.bold}[FATAL] ${backendIdent.conflicto}${colors.reset}\n`);
      cleanupAndExit(1);
    }
  } else {
    // Si el backend está apagado y el JAR no existe, no es ejecutable o está desactualizado:
    const requiereCompilacion = !estadoJar.existe || !estadoJar.esEjecutable || (estadoJar.desactualizado && !permitirJarDesactualizado);
    if (requiereCompilacion) {
      const motivo = !estadoJar.existe
        ? 'el archivo JAR no existe'
        : !estadoJar.esEjecutable
          ? 'el JAR no contiene el manifiesto ejecutable de Spring Boot'
          : `el código fuente es más reciente (${path.basename(estadoJar.archivo)})`;
      logInfo(`Generando artefacto ejecutable del backend con ./mvnw.cmd package -DskipTests (${motivo})...`);
      const compRes = compilarBackendJar();
      if (!compRes.success) {
        console.error(
          `\n${colors.red}${colors.bold}[FATAL] Falló la compilación del backend:\n${sanitizarSalida(compRes.stderr || compRes.stdout)}${colors.reset}\n`
        );
        cleanupAndExit(1);
      }
      estadoJar = comprobarJarActualizado();
      if (!estadoJar.existe || !estadoJar.esEjecutable) {
        console.error(`\n${colors.red}${colors.bold}[FATAL] El artefacto generado no es válido o ejecutable.${colors.reset}\n`);
        cleanupAndExit(1);
      }
      logOk('Artefacto backend compilado y verificado correctamente.');
    }

    logInfo('Iniciando proceso Java local con perfil clone en puerto 8080...');
    const psScript = INICIAR_BACKEND_SCRIPT.replace(/\\/g, '/');
    const psArgs = ['-ExecutionPolicy', 'Bypass', '-File', psScript, '-Perfil', 'clone', '-HttpPort', '8080'];

    let backendEarlyExit = false;
    let backendExitCode = null;
    const backendLogTail = [];

    startedBackendProcess = spawn('powershell', psArgs, {
      cwd: ROOT_DIR,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    startedBackendPid = startedBackendProcess.pid;
    startedBackendRecord = {
      role: 'backend',
      wrapperPid: startedBackendProcess.pid,
      wrapperProcess: startedBackendProcess,
      wrapperCmdPattern: 'iniciar-backend.ps1',
      childCmdPattern: 'lysandri-backend',
      knownPids: new Set([startedBackendProcess.pid]),
      startTime: Date.now(),
    };
    backendReused = false;

    const pushBackendLog = (data) => {
      const lines = data.toString().split(/\r?\n/).filter(Boolean);
      for (const line of lines) {
        backendLogTail.push(line);
        if (backendLogTail.length > 50) backendLogTail.shift();
      }
    };
    if (startedBackendProcess.stdout) startedBackendProcess.stdout.on('data', pushBackendLog);
    if (startedBackendProcess.stderr) startedBackendProcess.stderr.on('data', pushBackendLog);

    startedBackendProcess.on('exit', (code) => {
      backendEarlyExit = true;
      backendExitCode = code;
      if (!isShuttingDown) {
        console.error(`\n${colors.red}${colors.bold}[ALERTA] El proceso de backend ha terminado (código ${code}).${colors.reset}\n`);
      }
    });

    logStep('5/6', 'Esperando respuesta del backend en http://localhost:8080...');
    const timeoutSeconds = 75;
    const start = Date.now();
    let backendListo = false;

    while ((Date.now() - start) / 1000 < timeoutSeconds) {
      if (backendEarlyExit) {
        process.stdout.write('\n');
        console.error(
          `\n${colors.red}${colors.bold}[FATAL] El backend terminó prematuramente (código ${backendExitCode}) antes de estar disponible.${colors.reset}\n` +
          (backendLogTail.length > 0 ? `Últimas líneas del registro:\n${backendLogTail.slice(-15).join('\n')}\n` : '')
        );
        cleanupAndExit(1);
      }

      await sleep(2000);
      const elapsed = Math.round((Date.now() - start) / 1000);
      process.stdout.write(`  ${colors.gray}Esperando respuesta HTTP del backend (${elapsed}s / ${timeoutSeconds}s)...${colors.reset}\r`);

      const res = await httpGet('http://127.0.0.1:8080/api/v1/orders/cuentas-bancarias');
      if (res.ok && (res.status === 200 || res.statusCode === 200)) {
        let validJson = false;
        try {
          const parsed = JSON.parse(res.body);
          if (Array.isArray(parsed) || typeof parsed === 'object') validJson = true;
        } catch (_) {}
        if (validJson) {
          backendListo = true;
          break;
        }
      }
    }
    process.stdout.write('\n');

    if (!backendListo) {
      console.error(`\n${colors.red}${colors.bold}[FATAL] El backend no respondió en el puerto 8080 dentro del tiempo límite establecido.${colors.reset}\n`);
      cleanupAndExit(1);
    }

    const javaPortPid = buscarProcesoEnPuerto(8080);
    if (javaPortPid && startedBackendRecord) {
      startedBackendRecord.knownPids.add(javaPortPid);
    }
    logOk('Backend local operativo y respondiendo en http://localhost:8080.');
  }

  // 5. Asegurar Frontend Vite en puerto 3000
  logStep('6/6', 'Iniciando frontend Vite en http://127.0.0.1:3000 (puerto estricto 3000)...');
  const frontendIdent = await verificarIdentidadFrontend({ port: 3000 });

  if (frontendIdent.activo) {
    if (frontendIdent.esNuestro) {
      logOk(`Frontend Vite ya activo y verificado en http://127.0.0.1:3000 (PID ${frontendIdent.pid}). Se reutiliza.`);
      frontendReused = true;
    } else {
      console.error(`\n${colors.red}${colors.bold}[FATAL] ${frontendIdent.conflicto}${colors.reset}\n`);
      cleanupAndExit(1);
    }
  } else {
    logInfo('Iniciando Vite...');
    const viteBin = path.resolve(FRONTEND_DIR, 'node_modules', 'vite', 'bin', 'vite.js');
    startedFrontendProcess = spawn(process.execPath, [viteBin, '--host', '127.0.0.1', '--port', '3000', '--strictPort', '--no-open'], {
      cwd: FRONTEND_DIR,
      stdio: ['ignore', 'inherit', 'inherit'],
      windowsHide: true,
    });
    startedFrontendPid = startedFrontendProcess.pid;
    startedFrontendRecord = {
      role: 'frontend',
      wrapperPid: startedFrontendProcess.pid,
      wrapperProcess: startedFrontendProcess,
      wrapperCmdPattern: 'vite',
      childCmdPattern: 'vite',
      knownPids: new Set([startedFrontendProcess.pid]),
      startTime: Date.now(),
    };
    frontendReused = false;

    let processExitedEarly = false;
    let exitCodeReceived = null;
    startedFrontendProcess.on('exit', (code) => {
      processExitedEarly = true;
      exitCodeReceived = code;
      if (!isShuttingDown) {
        console.error(`\n${colors.red}${colors.bold}[ALERTA] El proceso de Vite terminó (código ${code}).${colors.reset}\n`);
      }
    });

    const timeoutSeconds = 25;
    const start = Date.now();
    let frontendListo = false;

    while ((Date.now() - start) / 1000 < timeoutSeconds) {
      if (processExitedEarly) {
        console.error(
          `\n${colors.red}${colors.bold}[FATAL] El servidor Vite terminó prematuramente (código ${exitCodeReceived}) antes de estar disponible.${colors.reset}\n`
        );
        cleanupAndExit(1);
      }

      await sleep(600);

      // Usar la MISMA verificación estricta para servicios recién iniciados y reutilizados
      const ident = await verificarIdentidadFrontend({
        port: 3000,
        expectedSig: WORKSPACE_SIGNATURE,
        startedRecord: startedFrontendRecord,
      });

      if (ident.activo) {
        if (ident.esNuestro) {
          frontendListo = true;
          break;
        }
        if (ident.estado === 'inicializando' || ident.transitorio) {
          // Proceso propio inicializándose: continuar esperando sin abortar
          continue;
        }
        if (ident.estado === 'identidad_invalida' || ident.estado === 'proceso_ajeno') {
          // Conflicto real: conservar proceso ajeno y abortar limpiando únicamente los propios
          console.error(
            `\n${colors.red}${colors.bold}[FATAL] Conflicto de puerto durante el inicio de Vite: ${ident.conflicto}${colors.reset}\n`
          );
          cleanupAndExit(1);
        }
      }
    }

    if (!frontendListo) {
      console.error(
        `\n${colors.red}${colors.bold}[FATAL] El servidor Vite no respondió con identidad autorizada en http://127.0.0.1:3000 dentro del tiempo límite establecido.${colors.reset}\n`
      );
      cleanupAndExit(1);
    }
    const vitePortPid = buscarProcesoEnPuerto(3000);
    if (vitePortPid && startedFrontendRecord) {
      startedFrontendRecord.knownPids.add(vitePortPid);
    }
    logOk('Frontend Vite verificado y disponible en http://127.0.0.1:3000.');
  }

  // 6. Verificación de proxy Vite -> Backend antes de abrir la plataforma
  logInfo('Verificando proxy Vite -> Backend (http://127.0.0.1:3000/api/v1/programas)...');
  let proxyListo = false;
  for (let i = 0; i < 10; i++) {
    const pRes = await httpGet('http://127.0.0.1:3000/api/v1/programas', 2000);
    if (pRes.ok && (pRes.status === 200 || pRes.statusCode === 200)) {
      proxyListo = true;
      break;
    }
    await sleep(600);
  }
  if (proxyListo) {
    logOk('Proxy Vite -> Backend (/api) confirmado y operativo.');
  } else {
    logWarn('El proxy Vite -> Backend tardó en responder. Continuando verificación.');
  }

  // 7. Apertura del navegador
  if (!noAbrirNavegador) {
    logInfo('Abriendo navegador en http://127.0.0.1:3000...');
    try {
      const openCmd =
        process.platform === 'win32'
          ? 'start "" "http://127.0.0.1:3000"'
          : process.platform === 'darwin'
            ? 'open "http://127.0.0.1:3000"'
            : 'xdg-open "http://127.0.0.1:3000"';
      exec(openCmd, { windowsHide: true });
      logOk('Navegador abierto.');
    } catch (err) {
      logWarn(`No se pudo abrir el navegador automáticamente: ${err.message}`);
    }
  }

  // Resumen de estado
  console.log(`\n${colors.green}${colors.bold}================================================================================${colors.reset}`);
  console.log(`${colors.green}${colors.bold}PLATAFORMA DISPONIBLE${colors.reset}`);
  console.log(`${colors.green}${colors.bold}================================================================================${colors.reset}`);
  console.log(`  ${colors.bold}Frontend Web:${colors.reset}       http://127.0.0.1:3000`);
  console.log(`  ${colors.bold}Backend API:${colors.reset}        http://localhost:8080`);
  console.log(`  ${colors.bold}PostgreSQL Clon:${colors.reset}    127.0.0.1:5434/lysandri_clone_db`);
  console.log(
    `  ${colors.bold}Iniciados aquí:${colors.reset}     ${[startedBackendPid ? `Backend (PID ${startedBackendPid})` : null, startedFrontendPid ? `Vite (PID ${startedFrontendPid})` : null]
      .filter(Boolean)
      .join(', ') || 'Ninguno (todos reutilizados)'
    }`
  );
  console.log(
    `  ${colors.bold}Reutilizados:${colors.reset}       ${[backendReused ? 'Backend en puerto 8080' : null, frontendReused ? 'Frontend en puerto 3000' : null, 'Docker Desktop', 'PostgreSQL clone']
      .filter(Boolean)
      .join(', ')
    }`
  );
  console.log(`${colors.gray}Presione Ctrl+C para finalizar los procesos de esta sesión.${colors.reset}\n`);

  // Monitoreo continuo de salud
  setInterval(async () => {
    if (isShuttingDown) return;
    const res = await httpGet('http://127.0.0.1:8080/api/v1/orders/cuentas-bancarias', 2000);
    if (!res.ok) {
      console.error(
        `\n${colors.red}${colors.bold}[ALERTA] Se ha perdido la conexión con el backend en http://localhost:8080. La plataforma no está completamente disponible.${colors.reset}\n`
      );
    }
  }, 5000);
}

// -----------------------------------------------------------------------------
// ENLACE CLI
// -----------------------------------------------------------------------------
process.on('SIGINT', () => cleanupAndExit(0));
process.on('SIGTERM', () => cleanupAndExit(0));
if (process.stdin && typeof process.stdin.on === 'function') {
  process.stdin.on('data', (chunk) => {
    const s = chunk.toString().trim();
    if (s === 'exit' || s === 'quit' || s === 'stop' || s === 'SIGINT' || s === 'q') {
      cleanupAndExit(0);
    }
  });
}

async function main() {
  const args = process.argv.slice(2);
  const SOLO_VERIFICAR = args.includes('--solo-verificar');
  const PERMITIR_JAR_DESACTUALIZADO = args.includes('--permitir-jar-desactualizado');
  const NO_ABRIR_NAVEGADOR = args.includes('--no-abrir-navegador');

  if (SOLO_VERIFICAR) {
    const res = await ejecutarSoloVerificar({
      permitirJarDesactualizado: PERMITIR_JAR_DESACTUALIZADO,
    });
    process.exit(res.exito ? 0 : 1);
  }

  await iniciarEntorno({
    permitirJarDesactualizado: PERMITIR_JAR_DESACTUALIZADO,
    noAbrirNavegador: NO_ABRIR_NAVEGADOR,
  });
}

const isDirectCall = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename);
if (isDirectCall) {
  main().catch((err) => {
    console.error(`\n${colors.red}[FATAL] Error inesperado: ${err.message}${colors.reset}\n`);
    cleanupAndExit(1);
  });
}
