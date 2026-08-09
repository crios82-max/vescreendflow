import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Header } from '../components/Header'
import { Footer } from '../components/Footer'
import { api, saveSession } from '../lib/api'
import './Auth.css'

export function Login() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const data = new FormData(e.currentTarget)
    const email = String(data.get('email') || '')
    const password = String(data.get('password') || '')

    if (!email || !password) {
      setError('Ingresa correo y contraseña.')
      return
    }

    setLoading(true)
    setError('')
    try {
      const session = await api.login({ email, password })
      saveSession(session)
      navigate('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al iniciar sesión')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Header variant="white" />
      <main className="auth">
        <form className="auth-card" onSubmit={handleSubmit}>
          <h1>Iniciar sesión</h1>
          <p>Accede a tu panel de vescreenflow</p>
          <p className="auth-hint">Demo: demo@vescreenflow.com / password123</p>
          {error ? <div className="auth-error">{error}</div> : null}
          <label>
            Correo
            <input
              name="email"
              type="email"
              required
              defaultValue="demo@vescreenflow.com"
              placeholder="tu@empresa.com"
            />
          </label>
          <label>
            Contraseña
            <input
              name="password"
              type="password"
              required
              defaultValue="password123"
              placeholder="••••••••"
            />
          </label>
          <button type="submit" className="btn btn-navy" disabled={loading}>
            {loading ? 'Entrando…' : 'Iniciar sesión'}
          </button>
          <p className="auth-switch">
            ¿No tienes cuenta? <Link to="/signup">Crear cuenta</Link>
          </p>
        </form>
      </main>
      <Footer />
    </>
  )
}
