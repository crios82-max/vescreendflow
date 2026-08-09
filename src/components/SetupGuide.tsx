import './SetupGuide.css'

const steps = [
  {
    title: 'Paso 1',
    text: 'Necesitas un TV o cualquier pantalla digital; no tiene que ser smart TV. Puede ser de consumo o comercial. Cualquier TV sirve.',
    icon: '📺',
  },
  {
    title: 'Paso 2',
    text: 'Necesitas un reproductor: Amazon Signage Stick, Android Box, Windows Media Player, Raspberry Pi o casi cualquier dispositivo. Recomendamos Amazon Signage Stick.',
    icon: '📟',
  },
  {
    title: 'Paso 3',
    text: 'Abre https://vescreenflow.com/play en el dispositivo (Chrome en pantalla completa). Verás un código de 8 dígitos. En el panel de vescreenflow haz clic en “Agregar pantalla”, ingresa el código y listo.',
    icon: '🔗',
  },
]

export function SetupGuide() {
  return (
    <section className="setup section-pad" id="setup">
      <div className="container">
        <h2 className="section-title">Guía rápida de configuración</h2>
        <div className="setup__grid">
          {steps.map((step) => (
            <article key={step.title} className="setup-card">
              <div className="setup-card__icon" aria-hidden="true">
                {step.icon}
              </div>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </article>
          ))}
        </div>
        <p className="setup__note">
          También tenemos guías rápidas para Raspberry Pi, Amazon Firestick y Windows Media
          Players
        </p>
      </div>
    </section>
  )
}
