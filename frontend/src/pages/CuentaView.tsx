import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { conversionPrueba } from '../services/conversionPrueba';
import { DemostracionComercial } from '../components/accounting/DemostracionComercial';
import { authService, getApiErrorMessage } from '../services/authService';

export const CuentaView: React.FC = () => {
  const { user, isLoading, logout } = useAuth();
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [completado, setCompletado] = useState(false);
  const [pruebas,setPruebas] = useState(false);
  const cliente = user?.rol === 'ESTUDIANTE';
  useEffect(() => {
    if (!cliente) return;
    let activo=true;
    void conversionPrueba.disponible().then(disponible => {if(activo) setPruebas(disponible);}).catch(() => {});
    return () => {activo=false;};
  }, [cliente]);
  if (isLoading) return <p className="p-8">Verificando sesión...</p>;
  if (!user && !completado) return <Navigate to="/intranet" replace />;
  const enviar = async (event: React.FormEvent) => {
    event.preventDefault();
    if (enviando) return;
    setError('');
    if (nueva !== confirmacion) { setError('Las contraseñas no coinciden.'); return; }
    if (nueva.length < 10 || new TextEncoder().encode(nueva).length > 72) { setError('Usa al menos 10 caracteres y hasta 72 bytes.'); return; }
    setEnviando(true);
    try {
      const resultado = await authService.cambiarPassword(actual, nueva);
      setMensaje(resultado.mensaje); setCompletado(true); setActual(''); setNueva(''); setConfirmacion(''); logout();
    } catch (e) { setError(getApiErrorMessage(e)); }
    finally { setEnviando(false); }
  };
  return <main className="mx-auto max-w-xl px-6 py-12">
    <h1 className="mb-3 text-3xl font-bold">Mi cuenta</h1>
    {completado ? <>
      <p role="status" className="my-6">{mensaje}</p>
      <Link to="/intranet" className="font-semibold text-cyan-600">Volver a iniciar sesión</Link>
    </> : <>
      <p className="mb-8 text-sm">{user?.nombre} · {user?.email}</p>
      <h2 className="mb-2 text-xl font-semibold">Cambiar contraseña</h2>
      <p className="mb-6 text-sm">Confirma tu contraseña actual. El cambio cerrará las sesiones anteriores y generará un aviso al correo registrado.</p>
      {error && <p role="alert" className="mb-4 rounded border border-red-400 p-3 text-red-500">{error}</p>}
      <form onSubmit={enviar} className="space-y-5">
        <div><label htmlFor="claveActual" className="block text-sm font-semibold">Contraseña actual</label>
          <input id="claveActual" type="password" required maxLength={200} autoComplete="current-password" value={actual} disabled={enviando} onChange={e=>setActual(e.target.value)} className="mt-2 w-full rounded border border-slate-500 bg-transparent p-3" /></div>
        <div><label htmlFor="claveNueva" className="block text-sm font-semibold">Nueva contraseña</label>
          <input id="claveNueva" type="password" required minLength={10} maxLength={72} autoComplete="new-password" value={nueva} disabled={enviando} onChange={e=>setNueva(e.target.value)} className="mt-2 w-full rounded border border-slate-500 bg-transparent p-3" /></div>
        <div><label htmlFor="confirmarNueva" className="block text-sm font-semibold">Confirma la nueva contraseña</label>
          <input id="confirmarNueva" type="password" required minLength={10} maxLength={72} autoComplete="new-password" value={confirmacion} disabled={enviando} onChange={e=>setConfirmacion(e.target.value)} className="mt-2 w-full rounded border border-slate-500 bg-transparent p-3" /></div>
        <button type="submit" disabled={enviando} className="w-full rounded bg-cyan-400 px-5 py-3 font-bold text-slate-950 disabled:opacity-50">{enviando?'Guardando...':'Guardar nueva contraseña'}</button>
      </form>
      {cliente && pruebas && <div className="mt-8"><DemostracionComercial cliente /></div>}
    </>}
  </main>;
};
