import './Hardware.css'

const devices = [
  { name: 'Amazon Signage Stick', desc: 'Reproductor recomendado para pantallas comerciales' },
  { name: 'Amazon Fire Stick', desc: 'Económico y fácil de conseguir' },
  { name: 'Android Box', desc: 'Gratis: Chrome → vescreenflow.com/play (sin Play Store)' },
  { name: 'Windows Player', desc: 'Gratis: usa kiosk/kiosk-windows.bat con Chrome' },
  { name: 'Raspberry Pi', desc: 'Gratis: usa kiosk/kiosk-pi.sh con Chromium' },
  { name: 'Chrome / Navegador', desc: 'Gratis: abre /play en modo kiosco desde cualquier navegador' },
]

export function Hardware() {
  return (
    <section className="hardware section-pad" id="hardware">
      <div className="container">
        <h2 className="section-title">Hardware</h2>
        <h4 className="hardware__lead">
          Convierte cualquier pantalla en señalización digital inteligente con vescreenflow
          y tu reproductor favorito
        </h4>
        <p className="hardware__intro">
          vescreenflow funciona en la mayoría de reproductores; esta es nuestra lista de
          dispositivos recomendados y probados.
        </p>

        <div className="hardware__grid">
          {devices.map((device) => (
            <article key={device.name} className="hardware-card">
              <div className="hardware-card__icon" aria-hidden="true">
                ▣
              </div>
              <h3>{device.name}</h3>
              <p>{device.desc}</p>
            </article>
          ))}
        </div>

        <h5 className="hardware__os">
          También puedes instalar vescreenflow en reproductores con Android, Windows, Linux
          y ChromeOS.
        </h5>
        <p className="hardware__note">
          Seguimos agregando dispositivos. Si tienes un reproductor que no está en la lista
          y quieres usarlo con vescreenflow, ¡házmelo saber!
        </p>
      </div>
    </section>
  )
}
