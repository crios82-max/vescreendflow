import type { Response } from 'express';

/** Respond with Spanish message + stable errorCode for client i18n. */
export function sendError(res: Response, status: number, error: string, errorCode: string) {
  return res.status(status).json({ error, errorCode });
}
