export default function Privacy() {
  return (
    <div className="auth-page">
      <div className="auth-card" style={{ maxWidth: 640 }}>
        <h1>Política de privacidad</h1>
        <p>Recopilamos email, teléfono, ubicación durante viajes activos y datos de pago procesados por Stripe.</p>
        <p>La ubicación se usa para matching, ETA y seguridad. No vendemos datos personales a terceros.</p>
        <p>Puedes solicitar eliminación de cuenta contactando soporte.</p>
        <p><a href="/login">Volver</a></p>
      </div>
    </div>
  );
}
