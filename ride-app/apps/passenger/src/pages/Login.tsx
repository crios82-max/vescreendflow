import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api, useAuth, BrandMark } from '@ride-app/web-shared';

export default function Login() {
  const { login, user } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [forgotMsg, setForgotMsg] = useState('');

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <BrandMark size="lg" showTagline />
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error-text">{error}</p>}
        {forgotMsg && <p className="muted-text">{forgotMsg}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? 'Entrando...' : 'Entrar'}</button>
        <button type="button" className="link-btn" onClick={async () => {
          if (!email) { setError('Ingresa tu email'); return; }
          const r = await api.forgotPassword(email);
          setForgotMsg(r.devResetUrl ? `Dev: ${r.devResetUrl}` : 'Revisa tu email');
        }}>¿Olvidaste tu contraseña?</button>
        <button type="button" className="link-btn" onClick={() => {}}>
          <Link to="/register">Crear cuenta</Link>
        </button>
        <p className="muted-text"><Link to="/terms">Términos</Link> · <Link to="/privacy">Privacidad</Link></p>
      </form>
    </div>
  );
}
