import { Router, type Request, type Response } from 'express';
import { pool } from '../../db.js';

const router = Router();

router.post('/voice/connect', (req: Request, res: Response) => {
  const callee = (req.query.callee as string) ?? '';
  const from = process.env.TWILIO_PHONE_NUMBER ?? '';
  if (!callee || !from) {
    res.type('text/xml');
    return res.send('<?xml version="1.0" encoding="UTF-8"?><Response><Say>Error de conexión</Say></Response>');
  }

  res.type('text/xml');
  res.send(`<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say voice="alice" language="es-MX">Conectando llamada enmascarada de Ride.</Say>
  <Dial callerId="${from}" answerOnBridge="true">${callee}</Dial>
</Response>`);
});

router.post('/voice/status', async (req: Request, res: Response) => {
  const callSid = req.body.CallSid as string | undefined;
  const status = req.body.CallStatus as string | undefined;
  if (callSid && status) {
    await pool.query(
      `UPDATE call_sessions SET status = $1 WHERE twilio_call_sid = $2`,
      [status, callSid],
    ).catch(() => {});
  }
  res.sendStatus(204);
});

export default router;
