import { getStripe } from './stripe.js';
import { pool } from '../db.js';

const PLATFORM_FEE_PERCENT = Number(process.env.PLATFORM_FEE_PERCENT ?? 25);

export async function getOrCreateConnectAccount(driverUserId: string, email: string) {
  const stripe = getStripe();
  if (!stripe) return null;

  const profile = await pool.query(
    'SELECT stripe_connect_account_id FROM driver_profiles WHERE user_id = $1',
    [driverUserId],
  );
  let accountId = profile.rows[0]?.stripe_connect_account_id as string | null;

  if (!accountId) {
    const account = await stripe.accounts.create({
      type: 'express',
      email,
      capabilities: { card_payments: { requested: true }, transfers: { requested: true } },
      metadata: { driverUserId },
    });
    accountId = account.id;
    await pool.query(
      'UPDATE driver_profiles SET stripe_connect_account_id = $1 WHERE user_id = $2',
      [accountId, driverUserId],
    );
  }

  return accountId;
}

export async function createConnectOnboardingLink(driverUserId: string, email: string) {
  const stripe = getStripe();
  if (!stripe) return null;

  const accountId = await getOrCreateConnectAccount(driverUserId, email);
  if (!accountId) return null;

  const refreshUrl = process.env.STRIPE_CONNECT_REFRESH_URL ?? 'http://localhost:5175';
  const returnUrl = process.env.STRIPE_CONNECT_RETURN_URL ?? 'http://localhost:5175';

  const link = await stripe.accountLinks.create({
    account: accountId,
    refresh_url: refreshUrl,
    return_url: returnUrl,
    type: 'account_onboarding',
  });

  return { url: link.url, accountId };
}

export async function getConnectStatus(driverUserId: string) {
  const stripe = getStripe();
  const profile = await pool.query(
    'SELECT stripe_connect_account_id, stripe_connect_onboarded FROM driver_profiles WHERE user_id = $1',
    [driverUserId],
  );
  const accountId = profile.rows[0]?.stripe_connect_account_id as string | null;
  if (!stripe || !accountId) {
    return { connected: false, accountId: null, chargesEnabled: false, payoutsEnabled: false };
  }

  const account = await stripe.accounts.retrieve(accountId);
  const onboarded = account.charges_enabled && account.payouts_enabled;
  if (onboarded && !profile.rows[0].stripe_connect_onboarded) {
    await pool.query(
      'UPDATE driver_profiles SET stripe_connect_onboarded = TRUE WHERE user_id = $1',
      [driverUserId],
    );
  }

  return {
    connected: true,
    accountId,
    chargesEnabled: account.charges_enabled,
    payoutsEnabled: account.payouts_enabled,
    onboarded,
  };
}

export async function buildPaymentIntentParams(
  totalCents: number,
  rideId: string,
  tipAmount: number,
  driverConnectAccountId: string | null,
) {
  const base: Record<string, unknown> = {
    amount: totalCents,
    currency: 'usd',
    metadata: { rideId, tipAmount: String(tipAmount) },
  };

  if (driverConnectAccountId) {
    const fee = Math.round(totalCents * (PLATFORM_FEE_PERCENT / 100));
    return {
      ...base,
      application_fee_amount: fee,
      transfer_data: { destination: driverConnectAccountId },
    };
  }

  return base;
}
