import './Tutorials.css'

const videos = [
  {
    title: 'Tutorial completo de señalización digital',
    desc: 'Desde el registro hasta tu primera pantalla en vivo, en un solo recorrido.',
  },
  {
    title: 'Funciones avanzadas en vescreenflow',
    desc: 'Horarios, sincronización, marca blanca y más.',
  },
  {
    title: 'Lo básico de la señalización con vescreenflow',
    desc: 'Introducción fácil a playlists y contenido.',
  },
]

export function Tutorials() {
  return (
    <section className="tutorials section-pad" id="tutorials">
      <div className="container">
        <h2 className="section-title">Tutoriales en video</h2>
        <div className="tutorials__grid">
          {videos.map((video) => (
            <article key={video.title} className="tutorial-card">
              <div className="tutorial-card__thumb" aria-hidden="true">
                <span>▶</span>
              </div>
              <h3>{video.title}</h3>
              <p>{video.desc}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
