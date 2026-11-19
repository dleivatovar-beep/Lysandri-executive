import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { authService, getApiErrorMessage, type CondicionesRegistro } from '../services/authService';

const campos = [
  { nombre: 'nombre', etiqueta: 'Nombres', tipo: 'text', completar: 'given-name', maximo: 100 },
  { nombre: 'apellidos', etiqueta: 'Apellidos', tipo: 'text', completar: 'family-name', maximo: 100 },
  { nombre: 'email', etiqueta: 'Correo electrónico', tipo: 'email', completar: 'email', maximo: 150 },
  { nombre: 'telefono', etiqueta: 'Teléfono (opcional)', tipo: 'tel', completar: 'tel', maximo: 20 },
  { nombre: 'password', etiqueta: 'Contraseña (mínimo 10 caracteres)', tipo: 'password', completar: 'new-password', maximo: 72 },
  { nombre: 'confirmacion', etiqueta: 'Confirma tu contraseña', tipo: 'password', completar: 'new-password', maximo: 72 },
] as const;

export function RegistroCliente() {
  const [condiciones, setCondiciones] = useState<CondicionesRegistro | null>();
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const enviando = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    void authService.condicionesRegistro(controller.signal).then(datos => {
      if (!controller.signal.aborted) setCondiciones(datos);
    }).catch(() => {
      if (!controller.signal.aborted) setCondiciones(null);
    });
    return () => controller.abort();
  }, []);

  const registrar = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!condiciones || enviando.current) return;
    setError('');
    const datos = new FormData(event.currentTarget);
    const campo = (nombre: string) => String(datos.get(nombre) ?? '');
    const password = campo('password');
    const nombre = campo('nombre').trim();
    const apellidos = campo('apellidos').trim();
    const aceptaTerminos = datos.has('terminos');
    const aceptaPrivacidad = datos.has('privacidad');
    if (!nombre || !apellidos || !aceptaTerminos || !aceptaPrivacidad) {
      setError('Completa tus nombres y apellidos y acepta las condiciones.');
      return;
    }
    if (password !== campo('confirmacion') || password.length < 10 || new TextEncoder().encode(password).length > 72) {
      setError('Ingresa la misma contraseña en ambos campos. Debe tener al menos 10 caracteres y hasta 72 bytes.');
      return;
    }
    enviando.current = true;
    setOcupado(true);
    try {
      setMensaje(await authService.register({
        nombre, apellidos, email: campo('email').trim().toLowerCase(), password,
        telefono: campo('telefono').trim() || undefined,
        aceptaTerminos, aceptaPrivacidad, version: condiciones.version,
      }));
    } catch (fallo) {
      setError(getApiErrorMessage(fallo));
    } finally {
      enviando.current = false;
      setOcupado(false);
    }
  };

  return <main className="mx-auto w-full max-w-2xl px-6 py-12">
    <h1 className="mb-5 text-3xl font-bold">Crear cuenta de cliente</h1>
    {condiciones === undefined && <p role="status">Consultando disponibilidad del registro…</p>}
    {condiciones === null && <p role="status">El registro de clientes no está disponible en este entorno.</p>}
    {mensaje && <div className="space-y-4">
      <p role="status">{mensaje}</p>
      <Link to="/activar-cuenta" className="font-semibold text-cyan-700 underline dark:text-cyan-300">Solicitar otro enlace</Link>
    </div>}
    {condiciones && !mensaje && <form onSubmit={registrar} className="space-y-5">
      <p>Crea una cuenta de prueba y verifica tu correo para activarla.</p>
      {error && <p role="alert" className="rounded border border-red-400 p-3 text-sm text-red-700 dark:text-red-300">{error}</p>}
      <fieldset disabled={ocupado} className="grid gap-4 sm:grid-cols-2">
        <legend className="sr-only">Datos de la cuenta</legend>
        {campos.map(campo => <label key={campo.nombre} className="block text-sm font-semibold">
          {campo.etiqueta}
          <input name={campo.nombre} type={campo.tipo} autoComplete={campo.completar} maxLength={campo.maximo}
            minLength={campo.tipo === 'password' ? 10 : undefined} required={campo.nombre !== 'telefono'}
            className="mt-1 block w-full rounded-md border border-slate-400 bg-transparent px-3 py-3 dark:border-slate-600" />
        </label>)}
      </fieldset>
      <fieldset disabled={ocupado} className="space-y-3 rounded-md border border-slate-400 p-4 text-sm dark:border-slate-600">
        <legend className="px-2 font-semibold">Condiciones de prueba</legend>
        <p>{condiciones.terminos}</p>
        <label className="flex items-start gap-2"><input type="checkbox" name="terminos" required />Acepto los términos de registro.</label>
        <p>{condiciones.privacidad}</p>
        <label className="flex items-start gap-2"><input type="checkbox" name="privacidad" required />Acepto el tratamiento de datos descrito.</label>
      </fieldset>
      <button type="submit" disabled={ocupado} className="rounded-md bg-cyan-400 px-5 py-3 font-bold text-slate-950 disabled:opacity-60">
        {ocupado ? 'Creando cuenta…' : 'Crear cuenta de prueba'}
      </button>
    </form>}
    <Link to="/intranet" className="mt-6 inline-block font-semibold text-cyan-700 underline dark:text-cyan-300">Iniciar sesión</Link>
  </main>;
}
