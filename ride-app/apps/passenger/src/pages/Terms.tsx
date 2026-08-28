import { BRAND } from '@ride-app/shared';

export default function Terms() {
  return (
    <div className="auth-page">
      <div className="auth-card" style={{ maxWidth: 640 }}>
        <h1>Términos de servicio</h1>
        <p>Al usar {BRAND.name} aceptas que el servicio conecta pasajeros con conductores independientes. Los precios son estimados y pueden variar por demanda.</p>
        <p>Los conductores son responsables de cumplir las leyes de tránsito locales. La plataforma no garantiza disponibilidad continua del servicio.</p>
        <p>Cancelaciones tardías pueden generar cargos según la política vigente.</p>
        <p><a href="/login">Volver</a></p>
      </div>
    </div>
  );
}
