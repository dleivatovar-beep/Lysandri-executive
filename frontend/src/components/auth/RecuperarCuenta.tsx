import React, { useState } from 'react';
import { authService, getApiErrorMessage } from '../../services/authService';

interface Props {
  token: string;
  emailInicial?: string;
  onBack: () => void;
  onCambio: () => void;
}

export const RecuperarCuenta: React.FC<Props> = ({ token, emailInicial = '', onBack, onCambio }) => {
  const [email, setEmail] = useState(emailInicial);
  const [password, setPassword] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState('');
  const [error, setError] = useState('');
  const [completado, setCompletado] = useState(false);

  const enviar = async (event: React.FormEvent) => {
    event.preventDefault();
    if (enviando || completado) return;
    setError('');
    setMensaje('');
    if (token && !/^[A-Za-z0-9_-]{43}$/.test(token)) { setError('El enlace no es válido. Solicita uno nuevo.'); return; }
    if (token && (password.length < 10 || new TextEncoder().encode(password).length > 72)) {
      setError('La contraseña debe tener al menos 10 caracteres y no superar 72 bytes.'); return;
    }
    if (token && password !== confirmacion) { setError('Las contraseñas no coinciden.'); return; }
    setEnviando(true);
    try {
      const respuesta = token
        ? await authService.restablecerPassword(token, password)
        : await authService.recuperarPassword(email.trim().toLowerCase(), window.location.origin);
      setMensaje(respuesta.mensaje);
      if (token) {
        setCompletado(true);
        setPassword('');
        setConfirmacion('');
        onCambio();
      }
    } catch (fallo) { setError(getApiErrorMessage(fallo)); }
    finally { setEnviando(false); }
  };

  return <div>
    {error && <div role="alert" className="lx-login-alert is-error mb-5 px-4 py-3 text-sm">{error}</div>}
    {mensaje && <div role="status" className="lx-login-alert is-success mb-5 px-4 py-3 text-sm">{mensaje}</div>}
    {!completado && <form onSubmit={enviar} className="lx-login-form space-y-4">
      {token ? <>
        <div className="lx-login-field">
          <label htmlFor="nuevaClave" className="mb-1.5 block text-xs font-semibold">Nueva contraseña</label>
          <input id="nuevaClave" type="password" autoComplete="new-password" minLength={10} required value={password}
            disabled={enviando} onChange={e => setPassword(e.target.value)} className="h-12 w-full px-4 text-sm" />
        </div>
        <div className="lx-login-field">
          <label htmlFor="confirmarClave" className="mb-1.5 block text-xs font-semibold">Confirma la nueva contraseña</label>
          <input id="confirmarClave" type="password" autoComplete="new-password" minLength={10} required value={confirmacion}
            disabled={enviando} onChange={e => setConfirmacion(e.target.value)} className="h-12 w-full px-4 text-sm" />
        </div>
      </> : <div className="lx-login-field">
        <label htmlFor="correoRecuperacion" className="mb-1.5 block text-xs font-semibold">Correo de tu cuenta</label>
        <input id="correoRecuperacion" type="email" autoComplete="email" maxLength={150} required value={email}
          disabled={enviando} onChange={e => setEmail(e.target.value)} className="h-12 w-full px-4 text-sm" />
      </div>}
      <button type="submit" disabled={enviando} className="lx-login-submit mt-6 flex h-12 w-full items-center justify-center text-sm font-bold">
        {enviando ? 'Procesando...' : token ? 'Guardar nueva contraseña' : 'Enviar enlace de recuperación'}
      </button>
    </form>}
    <button type="button" disabled={enviando} onClick={onBack} className="lx-login-link mt-5 text-sm">
      Volver a iniciar sesión
    </button>
  </div>;
};
