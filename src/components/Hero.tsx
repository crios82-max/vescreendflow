import { Link } from 'react-router-dom'
import './Hero.css'

export function Hero() {
  return (
    <section className="hero" id="software">
      <div className="container hero__grid">
        <div className="hero__copy">
          <h1>Pon tus pantallas digitales en marcha en minutos</h1>
          <h2>
            Empieza gratis con 10 pantallas y escala a pantallas, playlists y ubicaciones
            ilimitadas a medida que creces.
          </h2>
          <Link to="/signup" className="btn btn-navy hero__cta">
            Empieza gratis
          </Link>
          <p className="hero__tagline">
            ¡Gratis para empezar, hecho para crecer! Desde Caracas para toda Venezuela y
            Latinoamérica.
          </p>
        </div>

        <div className="hero__visual" aria-hidden="true">
          <div className="hero__screens">
            <div className="menu-board">
              <span>HAMBURGUESAS</span>
              <ul>
                <li>Clásica · $6.99</li>
                <li>Queso · $7.49</li>
                <li>Doble · $9.99</li>
              </ul>
            </div>
            <div className="menu-board menu-board--alt">
              <span>BEBIDAS</span>
              <ul>
                <li>Cola · $2.49</li>
                <li>Malteada · $4.99</li>
                <li>Café · $3.29</li>
              </ul>
            </div>
            <div className="menu-board">
              <span>ACOMPAÑANTES</span>
              <ul>
                <li>Papas · $3.49</li>
                <li>Aros · $3.99</li>
                <li>Ensalada · $4.49</li>
              </ul>
            </div>
            <div className="menu-board menu-board--alt">
              <span>COMBOS</span>
              <ul>
                <li>Combo · $11</li>
                <li>Familiar · $24</li>
                <li>Niños · $7.50</li>
              </ul>
            </div>
          </div>

          <div className="hero__badge">
            <strong>2026</strong>
            <span>VOTADO NÚMERO UNO</span>
            <em>FÁCIL DE USAR</em>
          </div>

          <div className="hero__wifi">📡</div>

          <div className="hero__stick" />

          <div className="hero__laptop">
            <div className="hero__laptop-screen">
              <div className="hero__laptop-bar">¿Qué vas a diseñar hoy?</div>
              <div className="hero__laptop-grid">
                <i />
                <i />
                <i />
                <i />
                <i />
                <i />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
