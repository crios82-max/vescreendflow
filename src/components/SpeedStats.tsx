import { Link } from 'react-router-dom'
import './SpeedStats.css'

export function SpeedStats() {
  return (
    <section className="speed section-pad">
      <div className="container">
        <h2 className="section-title">Señalización en vivo en menos de 2 minutos</h2>
        <div className="speed__grid">
          <div>
            <h4>Segundos</h4>
            <p>para registrarte</p>
          </div>
          <div>
            <h4>Segundos</h4>
            <p>para agregar una pantalla</p>
          </div>
          <div>
            <h4>Segundos</h4>
            <p>para subir contenido y salir al aire</p>
          </div>
        </div>
        <div className="speed__actions">
          <Link to="/signup" className="btn btn-navy">
            CREAR CUENTA GRATIS
          </Link>
          <a href="#tutorials" className="speed__tutorial">
            Tutorial paso a paso
          </a>
        </div>
      </div>
    </section>
  )
}
