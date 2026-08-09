import { Link } from 'react-router-dom'
import './Contact.css'

export function Contact() {
  return (
    <section className="contact section-pad" id="contact">
      <div className="container contact__grid">
        <div>
          <h2 className="section-title contact__title">Contáctanos</h2>
          <h3>Visítanos para una demo gratis</h3>
          <p>
            Pasa por nuestra oficina o agenda una cita y te mostramos lo fácil que es usar
            vescreenflow.
          </p>
          <h5>Dirección:</h5>
          <address>
            C.C. Terras Plaza
            <br />
            Nivel C1, Local 18
            <br />
            Urb. Terrazas del Club Hípico
            <br />
            Baruta, Caracas — Venezuela
            <br />
            <a href="mailto:hello@vescreenflow.com">¡Escríbenos!</a>
          </address>
          <p className="contact__alt">
            ¿No puedes venir? Hacemos demo por videollamada, o simplemente{' '}
            <Link to="/signup">crea una cuenta aquí</Link>.
          </p>
        </div>

        <form
          className="contact__form"
          onSubmit={(e) => {
            e.preventDefault()
            alert('¡Gracias! Te responderemos pronto.')
          }}
        >
          <label>
            Nombre
            <input name="name" required placeholder="Tu nombre" />
          </label>
          <label>
            Correo
            <input name="email" type="email" required placeholder="tu@empresa.com" />
          </label>
          <label>
            Mensaje
            <textarea
              name="message"
              rows={4}
              required
              placeholder="Cuéntanos sobre tus pantallas..."
            />
          </label>
          <button type="submit" className="btn btn-navy">
            Solicitar una demo
          </button>
        </form>
      </div>
    </section>
  )
}
