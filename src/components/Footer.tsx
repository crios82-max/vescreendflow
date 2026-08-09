import { Link } from 'react-router-dom'
import './Footer.css'

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer__grid">
        <div>
          <div className="site-footer__brand">vescreenflow</div>
          <p>
            Convierte cualquier TV en señalización digital. Gestiona pantallas a distancia
            con playlists, horarios y actualizaciones en tiempo real. Oficina en C.C. Terras
            Plaza, Caracas.
          </p>
        </div>

        <div>
          <h4>Empresa</h4>
          <ul>
            <li><a href="/#about">Sobre nosotros</a></li>
            <li><Link to="/pricing">Precios</Link></li>
            <li><a href="/#contact">Contacto</a></li>
            <li><Link to="/privacy">Política de privacidad</Link></li>
            <li><a href="#terms">Términos y condiciones</a></li>
          </ul>
        </div>

        <div>
          <h4>Producto</h4>
          <ul>
            <li><a href="/#features">¿Por qué nosotros?</a></li>
            <li><a href="/#hardware">Hardware</a></li>
            <li><a href="/#features">Marca blanca</a></li>
            <li><Link to="/pricing">Enterprise</Link></li>
            <li><a href="/#tutorials">Tutoriales</a></li>
          </ul>
        </div>

        <div>
          <h4>Descargas</h4>
          <ul>
            <li><a href="#android">APK Android</a></li>
            <li><a href="#windows">App Windows</a></li>
            <li><a href="#pi">App Raspberry Pi</a></li>
            <li><a href="#release">Registro de versiones</a></li>
          </ul>
        </div>
      </div>

      <div className="site-footer__bottom">
        <div className="container">
          Copyright © 2024 – 2026 vescreenflow.com. Todos los derechos reservados
        </div>
      </div>
    </footer>
  )
}
