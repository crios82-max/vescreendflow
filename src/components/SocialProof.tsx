import './SocialProof.css'

const platforms = [
  { label: 'signage stick', sub: 'Amazon' },
  { label: 'DISPONIBLE EN', sub: 'Google Play' },
  { label: 'DESCARGAR PARA', sub: 'Raspberry Pi' },
  { label: 'Instalar en', sub: 'Windows' },
  { label: 'disponible en', sub: 'amazon appstore' },
]

const logos = ["McDonald's", 'NHS', 'Berkeley', 'KFC', 'Best Western', 'Fairmont']

export function SocialProof() {
  return (
    <section className="social-proof section-pad">
      <div className="container">
        <div className="social-proof__badges">
          {platforms.map((p) => (
            <div key={p.sub} className="store-badge">
              <small>{p.label}</small>
              <strong>{p.sub}</strong>
            </div>
          ))}
        </div>

        <h4 className="social-proof__headline">
          Únete a más de 80,195 usuarios en más de 150 países que impulsan sus pantallas de
          señalización digital con vescreenflow, incluyendo:
        </h4>

        <div className="social-proof__logos">
          {logos.map((logo) => (
            <div key={logo} className="social-proof__logo">
              {logo}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
