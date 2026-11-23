import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://127.0.0.1/intranet' });
for (const name of ['window', 'document', 'HTMLElement', 'Node', 'MutationObserver', 'localStorage', 'navigator', 'FormData']) {
  Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] });
}
window.matchMedia = () => ({ matches: true });
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const { MemoryRouter } = await import('react-router-dom');
const { cleanup, fireEvent, render, screen } = await import('@testing-library/react');
const archivo = path.join(import.meta.dirname, '.prueba-registro.mjs');
const { outputFiles } = await esbuild.build({
  stdin: {
    contents: "export { LoginView } from './src/components/auth/LoginView'; export { RegistroCliente } from './src/pages/RegistroCliente'; export { AuthProvider } from './src/context/AuthContext';",
    resolveDir: import.meta.dirname,
  },
  bundle: true,
  platform: 'node',
  format: 'esm',
  write: false,
  loader: { '.gif': 'empty', '.png': 'empty', '.css': 'empty' },
  external: ['react', 'react-router-dom', 'axios'],
  plugins: [{
    name: 'api-de-prueba',
    setup(build) {
      build.onResolve({ filter: /^\.\/api$/ }, () => ({ path: 'api', namespace: 'prueba' }));
      build.onLoad({ filter: /^api$/, namespace: 'prueba' }, () => ({
        contents: 'export const apiClient = new Proxy({}, {get: (_, key) => globalThis.apiPrueba[key]}); export const isJwtExpired = () => false;',
      }));
      build.onResolve({ filter: /^\.\.\/services\/api$/ }, () => ({ path: 'api', namespace: 'prueba' }));
    },
  }],
});

try {
  await fs.writeFile(archivo, outputFiles[0].text);
  const { LoginView, AuthProvider, RegistroCliente } = await import(pathToFileURL(archivo).href);
  const montar = () => render(React.createElement(MemoryRouter, { initialEntries: ['/intranet'] },
    React.createElement(AuthProvider, null, React.createElement(LoginView))));
  const solicitudes = [];
  globalThis.apiPrueba = {
    get: async (url) => { throw new Error('Consulta inesperada: ' + url); },
    post: async (url, body) => {
      solicitudes.push({ url, body });
      if (solicitudes.length === 1) throw new Error('Conexión interrumpida');
      return { data: {
        token: 'token-de-prueba',
        idUser: 7,
        email: body.email,
        nombreCompleto: 'Ana Prueba',
        rol: 'CONTABILIDAD',
      } };
    },
  };

  montar();
  assert.deepEqual(screen.getAllByRole('button', { pressed: false }).map(button => button.textContent.trim()),
    ['Personal']);
  assert.equal(screen.queryByRole('button', { name: 'Crear cuenta', exact: true }), null);
  fireEvent.click(screen.getByRole('button', { name: 'Personal' }));
  assert.ok(screen.getByRole('heading', { name: 'Crea tu cuenta.' }));
  assert.equal(screen.queryByText('Condiciones y privacidad de prueba'), null);
  assert.equal(screen.getByLabelText('Teléfono').required, true);
  const llenar = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
  llenar('Nombres', 'Ana');
  llenar('Apellidos', 'Prueba');
  llenar('Correo electrónico', 'ANA@LYSANDRI.INVALID');
  llenar('Teléfono', '999123456');
  llenar('Área de acceso', 'CONTABILIDAD');
  llenar(/Contraseña [(]mínimo/, 'Prueba2026!');
  llenar('Confirma tu contraseña', 'Prueba2026!');
  const enviar = () => fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta y entrar' }));
  enviar();
  assert.equal((await screen.findByRole('alert')).textContent.trim(), 'Conexión interrumpida');
  assert.equal(screen.getByLabelText('Correo electrónico').value, 'ANA@LYSANDRI.INVALID');
  enviar();
  await screen.findByRole('button', { name: 'Crear cuenta y entrar' });
  assert.deepEqual(solicitudes, Array(2).fill({
    url: '/auth/register-interno',
    body: {
      nombres: 'Ana',
      apellidos: 'Prueba',
      email: 'ana@lysandri.invalid',
      password: 'Prueba2026!',
      telefono: '999123456',
      area: 'CONTABILIDAD',
    },
  }));
  assert.equal(localStorage.getItem('lysandri_token'), 'token-de-prueba');
  console.log('PASS Único formulario de alta interna, reintento y sesión');

  cleanup();
  localStorage.clear();
  const registroPublico = () => render(React.createElement(MemoryRouter, null,
    React.createElement(AuthProvider, null, React.createElement(RegistroCliente))));
  globalThis.apiPrueba.get = async () => { throw new Error('No disponible'); };
  registroPublico();
  await screen.findByText('El registro de clientes no está disponible en este entorno.');
  assert.equal(screen.queryByRole('button', { name: 'Crear cuenta' }), null);

  cleanup();
  globalThis.apiPrueba.get = async url => {
    assert.equal(url, '/auth/condiciones-registro');
    return { data: { version: 'prueba', terminos: 'Términos', privacidad: 'Privacidad' } };
  };
  const altas = [];
  globalThis.apiPrueba.post = async (url, body) => {
    altas.push({ url, body });
    return { data: { token: null, pendienteActivacion: true, mensaje: 'Verifica tu correo.' } };
  };
  montar();
  fireEvent.click(screen.getByRole('button', { name: 'Personal' }));
  const crear = await screen.findByRole('button', { name: 'Crear cuenta' });
  llenar('Nombres', 'Ana');
  llenar('Apellidos', 'Prueba');
  llenar('Correo electrónico', 'ANA@LYSANDRI.INVALID');
  llenar('Teléfono', '999123456');
  llenar('Contraseña (mínimo 10 caracteres)', 'Prueba2026!');
  llenar('Confirma tu contraseña', 'Prueba2026!');
  fireEvent.click(crear);
  assert.equal(altas.length, 0);
  fireEvent.click(screen.getByLabelText('Acepto los términos de registro.'));
  fireEvent.click(screen.getByLabelText('Acepto el tratamiento de datos descrito.'));
  fireEvent.click(crear);
  await screen.findByText('Verifica tu correo.');
  assert.ok(screen.getByRole('button', { name: 'Solicitar otro enlace' }));
  assert.equal(altas[0].url, '/auth/register');
  assert.equal(altas[0].body.email, 'ana@lysandri.invalid');
  assert.equal(altas[0].body.version, 'prueba');
  assert.equal(localStorage.getItem('lysandri_token'), null);
  console.log('PASS Registro público dentro de Personal, consentimientos y verificación sin sesión');

  cleanup();
  localStorage.clear();
  globalThis.apiPrueba.post = async (url, body) => {
    assert.equal(url, '/auth/register');
    return { data: { token: 'sesion-firmada-cliente', idUser: 41, email: body.email,
      nombreCompleto: 'Ana Prueba', rol: 'CLIENTE', pendienteActivacion: false } };
  };
  montar();
  fireEvent.click(screen.getByRole('button', { name: 'Personal' }));
  await screen.findByRole('button', { name: 'Crear cuenta' });
  llenar('Nombres', 'Ana');
  llenar('Apellidos', 'Prueba');
  llenar('Correo electrónico', 'ANA@LYSANDRI.INVALID');
  llenar('Teléfono', '999123456');
  llenar('Contraseña (mínimo 10 caracteres)', 'Prueba2026!');
  llenar('Confirma tu contraseña', 'Prueba2026!');
  fireEvent.click(screen.getByLabelText('Acepto los términos de registro.'));
  fireEvent.click(screen.getByLabelText('Acepto el tratamiento de datos descrito.'));
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
  await screen.findByRole('button', { name: 'Crear cuenta' });
  assert.equal(localStorage.getItem('lysandri_token'), 'sesion-firmada-cliente');
  assert.equal(JSON.parse(localStorage.getItem('lysandri_user')).rol, 'ESTUDIANTE');
  assert.equal(screen.queryByText('Verifica tu correo.'), null);
  console.log('PASS Registro de cliente inicia sesión y conserva el perfil sin privilegios internos');

  cleanup();
  localStorage.clear();
  globalThis.apiPrueba.post = async () => ({ data: { token: null, pendienteActivacion: false } });
  montar();
  fireEvent.click(screen.getByRole('button', { name: 'Personal' }));
  await screen.findByRole('button', { name: 'Crear cuenta' });
  llenar('Nombres', 'Ana');llenar('Apellidos', 'Prueba');
  llenar('Correo electrónico', 'ana@lysandri.invalid');llenar('Teléfono', '999123456');
  llenar('Contraseña (mínimo 10 caracteres)', 'Prueba2026!');llenar('Confirma tu contraseña', 'Prueba2026!');
  fireEvent.click(screen.getByLabelText('Acepto los términos de registro.'));
  fireEvent.click(screen.getByLabelText('Acepto el tratamiento de datos descrito.'));
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
  assert.match((await screen.findByRole('alert')).textContent, /no confirmó una sesión válida/);
  assert.equal(localStorage.getItem('lysandri_token'), null);
  console.log('PASS Una respuesta incompleta no se presenta como sesión iniciada');
} finally {
  cleanup();
  dom.window.close();
  await fs.rm(archivo, { force: true });
}
