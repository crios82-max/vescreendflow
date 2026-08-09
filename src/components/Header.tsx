import { Link, NavLink } from 'react-router-dom'
import './Header.css'

type HeaderProps = {
  variant?: 'yellow' | 'white'
}

export function Header({ variant = 'yellow' }: HeaderProps) {
  return (
    <header className={`site-header site-header--${variant}`}>
      <div className="container site-header__inner">
        <Link to="/" className="site-header__logo">
          vescreenflow
        </Link>

        <nav className="site-header__nav" aria-label="Principal">
          <a href="/#software">Software de señalización</a>
          <Link to="/pricing">Precios</Link>
          <a href="/#hardware">Hardware</a>
          <a href="/#setup">Cómo configurar</a>
          <Link to="/play">Player kiosk</Link>
        </nav>

        <div className="site-header__actions">
          <Link to="/login" className="btn btn-red">
            Iniciar sesión
          </Link>
          <Link
            to="/signup"
            className={variant === 'yellow' ? 'btn btn-outline site-header__create' : 'site-header__create-link'}
          >
            Crear cuenta
          </Link>
          <a href="/#contact" className="site-header__talk">
            Habla con nosotros
          </a>
        </div>

        <details className="site-header__mobile">
          <summary aria-label="Menú">☰</summary>
          <div className="site-header__mobile-panel">
            <a href="/#software">Software de señalización</a>
            <Link to="/pricing">Precios</Link>
            <a href="/#hardware">Hardware</a>
            <a href="/#setup">Cómo configurar</a>
            <Link to="/play">Player kiosk</Link>
            <NavLink to="/login">Iniciar sesión</NavLink>
            <NavLink to="/signup">Crear cuenta</NavLink>
          </div>
        </details>
      </div>
    </header>
  )
}
