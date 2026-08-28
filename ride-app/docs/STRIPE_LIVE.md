# Ride App — Stripe Live + Connect

Guía para pasar de test keys a **producción** en el Mac mini.

## 1. Dashboard Stripe

1. [dashboard.stripe.com](https://dashboard.stripe.com) → activa **Live mode** (toggle arriba).
2. **Developers → API keys** — copia:
   - Secret key → `sk_live_...`
   - Publishable key → `pk_live_...`

## 2. `.env` en el Mac mini (`ride-app/.env`)

```bash
STRIPE_SECRET_KEY=sk_live_...
VITE_STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...   # paso 3
PLATFORM_FEE_PERCENT=25
STRIPE_CONNECT_REFRESH_URL=http://localhost:5175
STRIPE_CONNECT_RETURN_URL=http://localhost:5175
```

Tras cambiar keys de Stripe **rebuild** las webs (publishable key va en el bundle):

```bash
cd ride-app
npm run build
pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env
```

## 3. Webhook (obligatorio en live)

**Developers → Webhooks → Add endpoint**

| Campo | Valor |
|-------|-------|
| URL | `https://movi-api.vescreenflow.com/webhooks/stripe` |
| Events | `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `account.updated` |

Copia el **Signing secret** (`whsec_...`) → `STRIPE_WEBHOOK_SECRET`.

Verificar (tras `pm2 restart ride-api`):

```bash
curl -sf https://movi-api.vescreenflow.com/health
# En Stripe → Webhook → Send test event → payment_intent.succeeded
```

## 4. Stripe Connect (payouts conductores)

1. **Connect → Settings** — activa Express accounts.
2. En la app, conductor web `:5175` → onboarding Connect.
3. El webhook `account.updated` marca `stripe_connect_onboarded` en DB.

URLs de return/refresh pueden quedarse en localhost si solo conductores usan la web en LAN. Para conductores remotos, expón `ride-driver.vescreenflow.com` (opcional, mismo túnel).

## 5. Apple Pay / Google Pay

En Stripe Dashboard → **Settings → Payment methods** — activa Apple Pay y Google Pay.

Dominio web (Apple Pay en browser):

- **Settings → Payment method domains** → añade `movi.vescreenflow.com`

## 6. Checklist rápido

```bash
cd ride-app
./scripts/check-prod.sh
```

- [ ] Keys `sk_live_` / `pk_live_` (no `sk_test_`)
- [ ] Webhook live apuntando a `movi-api.vescreenflow.com`
- [ ] `npm run build` después de cambiar `VITE_STRIPE_PUBLISHABLE_KEY`
- [ ] Pago de prueba real (monto bajo) desde `https://movi.vescreenflow.com`
- [ ] Conductor completa Connect y recibe payout de prueba

## 7. Rollback a test

Cambia las tres vars a test keys, rebuild, restart PM2. Desactiva o borra el endpoint webhook live si no lo usas.
