import { FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '@ride-app/web-shared';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }
    try {
      await api.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    }
  };

  if (!token) {
    return <div className="auth-page"><p className="error-text">Token inválido</p><Link to="/login">Volver</Link></div>;
  }

  if (done) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1>Contraseña actualizada</h1>
          <Link to="/login">Iniciar sesión</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Nueva contraseña</h1>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
        </label>
        <label>
          Confirmar
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required minLength={6} />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" type="submit">Guardar</button>
      </form>
    </div>
  );
}
