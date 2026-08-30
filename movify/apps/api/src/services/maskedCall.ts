import { pool } from '../db.js';

function twilioConfig() {
  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  const from = process.env.TWILIO_PHONE_NUMBER;
  if (!sid || !token || !from) return null;
  return { sid, token, from };
}

function apiPublicUrl() {
  return process.env.API_PUBLIC_URL ?? `http://localhost:${process.env.PORT ?? 4001}`;
}

export async function initiateMaskedCall(params: {
  rideId: string;
  callerUserId: string;
  callerPhone: string;
  calleePhone: string;
}) {
  const cfg = twilioConfig();
  if (!cfg) {
    return {
      mock: true,
      dialUrl: `tel:${params.calleePhone}`,
      hint: 'Twilio no configurado — llamada directa',
    };
  }

  const twimlUrl = `${apiPublicUrl()}/webhooks/twilio/voice/connect?callee=${encodeURIComponent(params.calleePhone)}`;
  const auth = Buffer.from(`${cfg.sid}:${cfg.token}`).toString('base64');
  const body = new URLSearchParams({
    To: params.callerPhone,
    From: cfg.from,
    Url: twimlUrl,
    StatusCallback: `${apiPublicUrl()}/webhooks/twilio/voice/status`,
    StatusCallbackEvent: 'completed',
  });

  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${cfg.sid}/Calls.json`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${auth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body,
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Twilio call failed: ${err}`);
  }

  const call = await res.json() as { sid: string };
  await pool.query(
    `INSERT INTO call_sessions (ride_id, caller_id, callee_phone, twilio_call_sid, status)
     VALUES ($1, $2, $3, $4, 'ringing')`,
    [params.rideId, params.callerUserId, params.calleePhone, call.sid],
  );

  return {
    initiated: true,
    masked: true,
    callSid: call.sid,
    message: 'Te llamaremos en unos segundos para conectar con tu contraparte',
  };
}

export function isTwilioVoiceEnabled() {
  return twilioConfig() !== null;
}
