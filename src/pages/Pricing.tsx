import { Link } from 'react-router-dom'
import { Header } from '../components/Header'
import { Footer } from '../components/Footer'
import './Pricing.css'

const plans = [
  {
    name: 'Gratis',
    price: '$0',
    detail: 'Primeras 10 pantallas',
    features: [
      '10 pantallas incluidas',
      'Playlists y horarios',
      'Subida de fotos y videos',
      'Actualizaciones remotas',
    ],
    cta: 'Empieza gratis',
    highlight: false,
  },
  {
    name: 'Growth',
    price: '$5',
    detail: 'por pantalla extra / mes',
    features: [
      'Pantallas ilimitadas',
      'Todo lo de Gratis',
      'Sincronización de pantallas',
      'Soporte prioritario',
    ],
    cta: 'Crear cuenta',
    highlight: true,
  },
  {
    name: 'Enterprise',
    price: 'A medida',
    detail: 'marca blanca y SLA',
    features: [
      'Marca blanca',
      'Soporte dedicado',
      'Integraciones personalizadas',
      'Precios por volumen',
    ],
    cta: 'Habla con nosotros',
    highlight: false,
  },
]

export function Pricing() {
  return (
    <>
      <Header variant="white" />
      <main className="pricing section-pad">
        <div className="container">
          <h1 className="section-title">Precios simples y rentables</h1>
          <p className="pricing__lead">
            Empieza gratis con 10 pantallas. Solo pagas las pantallas adicionales que uses
            a medida que creces. Precios en dólares (USD).
          </p>
          <div className="pricing__grid">
            {plans.map((plan) => (
              <article
                key={plan.name}
                className={`pricing-card${plan.highlight ? ' pricing-card--hot' : ''}`}
              >
                <h2>{plan.name}</h2>
                <div className="pricing-card__price">{plan.price}</div>
                <p>{plan.detail}</p>
                <ul>
                  {plan.features.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
                <Link
                  to={plan.name === 'Enterprise' ? '/#contact' : '/signup'}
                  className="btn btn-navy"
                >
                  {plan.cta}
                </Link>
              </article>
            ))}
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
