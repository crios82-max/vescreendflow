import './Clients.css'

const clients = [
  'Cadenas retail',
  'Restaurantes',
  'Salud',
  'Educación',
  'Hoteles',
  'Almacenes',
]

export function Clients() {
  return (
    <section className="clients section-pad">
      <div className="container">
        <h2 className="section-title">
          ¿Buscas el mejor software de señalización digital? Estás en buena compañía
        </h2>
        <p className="clients__lead">
          Algunos de los clientes que han anunciado en nuestro software o lo usan para
          alimentar sus pantallas digitales
        </p>
        <div className="clients__grid">
          {clients.map((client) => (
            <div key={client} className="clients__item">
              {client}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
