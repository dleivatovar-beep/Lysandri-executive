import assert from 'node:assert/strict';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import React from 'react';
const dom = new JSDOM('<html><body></body></html>', {url:'http://127.0.0.1:3000/intranet'});
globalThis.window=dom.window; globalThis.document=dom.window.document;
Object.defineProperty(globalThis,'navigator',{value:dom.window.navigator,configurable:true});
globalThis.HTMLElement=dom.window.HTMLElement; globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const {render,screen,fireEvent,waitFor,cleanup}=await import('@testing-library/react');
let calls=[]; let fail=false; let changed=0;
globalThis.recoveryMock={
 recuperarPassword:async (...args)=>{calls.push(args); if(fail) throw Error('Sin conexión'); return {mensaje:'Si la cuenta está habilitada, recibirás un enlace.'};},
 restablecerPassword:async (...args)=>{calls.push(args); if(fail) throw Error('El enlace venció'); return {mensaje:'Contraseña actualizada'};}
};
const output='.recuperacion-test.mjs';
try {
await esbuild.build({entryPoints:['src/components/auth/RecuperarCuenta.tsx'],outfile:output,bundle:true,format:'esm',platform:'node',external:['react'],plugins:[{name:'auth',setup(build){build.onResolve({filter:/services\/authService$/},()=>({path:'auth',namespace:'mock'}));build.onLoad({filter:/.*/,namespace:'mock'},()=>({contents:'export const authService=globalThis.recoveryMock; export const getApiErrorMessage=e=>e.message;',loader:'js'}));}}]});
const {RecuperarCuenta}=await import(pathToFileURL(process.cwd()+'/'+output));
const show=token=>render(React.createElement(RecuperarCuenta,{token,emailInicial:'ana@example.com',onBack:()=>{},onCambio:()=>changed++}));
show(''); assert.equal(screen.getByLabelText('Correo de tu cuenta').value,'ana@example.com');
fireEvent.click(screen.getByText('Enviar enlace de recuperación')); await screen.findByRole('status'); assert.deepEqual(calls.pop(),['ana@example.com','http://127.0.0.1:3000']); cleanup();
fail=true; show(''); fireEvent.click(screen.getByText('Enviar enlace de recuperación')); await screen.findByRole('alert'); await waitFor(()=>assert.equal(screen.getByText('Enviar enlace de recuperación').disabled,false)); cleanup();
fail=false; show('a'.repeat(43));
fireEvent.change(screen.getByLabelText('Nueva contraseña'),{target:{value:'NuevaClave123!'}}); fireEvent.change(screen.getByLabelText('Confirma la nueva contraseña'),{target:{value:'Diferente123!'}});
const before=calls.length; fireEvent.click(screen.getByText('Guardar nueva contraseña')); await screen.findByRole('alert'); assert.equal(calls.length,before);
fireEvent.change(screen.getByLabelText('Confirma la nueva contraseña'),{target:{value:'NuevaClave123!'}}); fireEvent.click(screen.getByText('Guardar nueva contraseña')); await screen.findByRole('status'); assert.equal(changed,1); assert.deepEqual(calls.at(-1),['a'.repeat(43),'NuevaClave123!']); assert.equal(screen.queryByLabelText('Nueva contraseña'),null); cleanup();
fail=true; show('a'.repeat(43)); for(const label of ['Nueva contraseña','Confirma la nueva contraseña']) fireEvent.change(screen.getByLabelText(label),{target:{value:'NuevaClave123!'}}); fireEvent.click(screen.getByText('Guardar nueva contraseña')); await screen.findByText('El enlace venció'); assert.equal(changed,1); cleanup();
console.log('PASS: correo inicial, solicitud, error recuperable, confirmación, cambio y enlace vencido.');
} finally {cleanup(); if(fs.existsSync(output)) fs.unlinkSync(output); dom.window.close();}
