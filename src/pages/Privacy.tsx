import { Link } from 'react-router-dom'
import './Legal.css'

export function Privacy() {
  return (
    <main className="legal">
      <div className="legal__card">
        <p className="legal__brand">
          <Link to="/">vescreenflow</Link>
        </p>
        <h1>Política de privacidad</h1>
        <p className="legal__updated">Última actualización: 9 de agosto de 2026</p>

        <p>
          vescreenflow (`vescreenflow.com`) es un servicio de señalización digital. Esta
          política describe qué datos tratamos al usar el sitio, el panel y la app Android
          player.
        </p>

        <h2>Datos que recopilamos</h2>
        <ul>
          <li>Cuenta: email y contraseña (almacenada de forma segura / hasheada).</li>
          <li>Contenido que subes: imágenes y videos para playlists.</li>
          <li>
            Pantallas: código de empareje, nombre, estado online/última conexión, playlist
            asignada y ajustes (rotación, horario).
          </li>
          <li>Datos técnicos mínimos del player (heartbeat) para saber si la pantalla está activa.</li>
        </ul>

        <h2>App Android</h2>
        <p>
          La app es un reproductor kiosk (WebView) que carga{' '}
          <code>https://vescreenflow.com/play</code>. No accede a contactos, fotos del
          dispositivo ni ubicación. Solo necesita Internet para mostrar el contenido de tu
          cuenta.
        </p>

        <h2>Uso de los datos</h2>
        <p>
          Operar el servicio, autenticar usuarios, sincronizar pantallas y mejorar
          estabilidad. No vendemos datos personales.
        </p>

        <h2>Conservación y seguridad</h2>
        <p>
          Conservamos los datos mientras la cuenta esté activa. Usamos HTTPS y controles de
          acceso en el servidor. Puedes solicitar eliminación de cuenta escribiendo a{' '}
          <a href="mailto:hola@vescreenflow.com">hola@vescreenflow.com</a>.
        </p>

        <h2>Contacto</h2>
        <p>
          C.C. Terras Plaza, Caracas ·{' '}
          <a href="mailto:hola@vescreenflow.com">hola@vescreenflow.com</a>
        </p>

        <p>
          <Link to="/">Volver al inicio</Link>
        </p>
      </div>
    </main>
  )
}
