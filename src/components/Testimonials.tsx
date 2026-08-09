import './Testimonials.css'

const reviews = [
  {
    flag: '🇺🇸',
    name: 'Ti',
    text: 'La forma más rentable de gestionar TVs a distancia. Tengo vescreenflow en casi 50 ubicaciones. Casi nunca me ha dado problemas.',
  },
  {
    flag: '🇬🇧',
    name: 'Jim Crow',
    text: 'El sistema es muy simple y fácil de usar. No soy bueno con las computadoras, pero logré tener mis pantallas en vivo en minutos. Gracias.',
  },
  {
    flag: '🇦🇺',
    name: "James O'Hara",
    text: '¡App muy eficiente y útil! Fácil de usar y de actualizar el contenido en la pantalla en tiempo real. Sin duda la usaré seguido en mi negocio.',
  },
  {
    flag: '🇨🇦',
    name: 'John Oliver',
    text: 'Llevo más de un año usando vescreenflow en mi almacén con 15 pantallas. La plataforma y el servicio son excepcionales.',
  },
]

export function Testimonials() {
  return (
    <section className="testimonials">
      <div className="container testimonials__grid">
        {reviews.map((review) => (
          <article key={review.name} className="review-card">
            <div className="review-card__flag" aria-hidden="true">
              {review.flag}
            </div>
            <div className="review-card__stars" aria-label="5 estrellas">
              ★★★★★
            </div>
            <p>{review.text}</p>
            <strong>{review.name}</strong>
          </article>
        ))}
      </div>
      <a href="#setup" className="testimonials__scroll" aria-label="Desplazar hacia abajo">
        ↓
      </a>
    </section>
  )
}
