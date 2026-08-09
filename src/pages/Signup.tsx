import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Header } from '../components/Header'
import { Footer } from '../components/Footer'
import { api, saveSession } from '../lib/api'
import './Auth.css'

export function Signup() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const data = new FormData(e.currentTarget)
    const name = String(data.get('name') || '')
    const email = String(data.get('email') || '')
    const password = String(data.get('password') || '')

    if (!name || !email || password.length < 6) {
      setError('Completa todos los campos. La contraseña debe tener al menos 6 caracteres.')
      return
    }

    setLoading(true)
    setError('')
    try {
      const session = await api.signup({ name, email, password })
      saveSession(session)
      navigate('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al crear la cuenta')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Header variant="white" />
      <main className="auth">
        <form className="auth-card" onSubmit={handleSubmit}>
          <h1>Crear cuenta gratis</h1>
          <p>Sin tarjeta ni compromiso</p>
          {error ? <div className="auth-error">{error}</div> : null}
          <label>
            Nombre completo
            <input name="name" required placeholder="Tu nombre" />
          </label>
          <label>
            Correo
            <input name="email" type="email" required placeholder="tu@empresa.com" />
          </label>
          <label>
            Contraseña
            <input
              name="password"
              type="password"
              required
              placeholder="Al menos 6 caracteres"
            />
          </label>
          <button type="submit" className="btn btn-navy" disabled={loading}>
            {loading ? 'Creando…' : 'Crear cuenta'}
          </button>
          <p className="auth-switch">
            ¿Ya tienes cuenta? <Link to="/login">Iniciar sesión</Link>
          </p>
        </form>
      </main>
      <Footer />
    </>
  )
}
