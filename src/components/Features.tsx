import './Features.css'

const features = [
  {
    title: 'Primeras 10 pantallas gratis*',
    text: 'Tus primeras diez pantallas son gratis; si necesitas más, solo pagas las pantallas adicionales que uses.',
  },
  {
    title: 'Actualizaciones en tiempo real',
    text: 'Puedes desplegar tus pantallas en minutos y cambiar el contenido en tiempo real.',
  },
  {
    title: 'Marca blanca',
    text: 'Sube tu propio logo en el panel y listo: tienes tu propia marca de señalización digital. Así puedes mostrar a tus clientes que tienes tu propio CMS.',
  },
  {
    title: 'Fácil de usar',
    text: 'Hicimos que configurar una pantalla sea súper simple. Puedes salir al aire en tres pasos: agrega una pantalla, crea una playlist y sube contenido.',
  },
  {
    title: 'Gestiona tus pantallas a distancia',
    text: 'Con vescreenflow puedes subir y administrar contenido sin visitar cada ubicación, ahorrando tiempo y dinero.',
  },
  {
    title: 'Programa contenido y crea playlists',
    text: 'Programa tu contenido con anticipación. Controlas la fecha y hora en que aparece, y así gestionas fácilmente lo que ven tus clientes.',
  },
  {
    title: 'Inicio automático al encender',
    text: 'Al mostrar contenido con la app, esta inicia automáticamente cuando enciendes la pantalla.',
  },
  {
    title: 'Rentable',
    text: 'El precio importa: por eso nuestros paquetes son más económicos cada vez que agregas otra pantalla.',
  },
  {
    title: 'Pantallas sincronizadas',
    text: '¿Quieres el mismo contenido en varias pantallas? ¡Sin problema! Puedes sincronizarlas con un clic.',
  },
  {
    title: 'Sube fotos y videos',
    text: 'Haz tus pantallas más atractivas con contenido dinámico y llamativo.',
  },
  {
    title: 'Escalabilidad simple',
    text: 'Facilitamos el crecimiento de tu red de pantallas con vescreenflow: pensamos en precio, hardware, facilidad de uso y marca blanca.',
  },
  {
    title: 'Soporte en línea gratis',
    text: 'Nuestros expertos están siempre en línea para ayudarte.',
  },
]

export function Features() {
  return (
    <section className="features section-pad" id="features">
      <div className="container">
        <h2 className="section-title">¿Por qué elegir vescreenflow?</h2>
        <div className="features__grid">
          {features.map((feature) => (
            <article key={feature.title} className="feature-item">
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
