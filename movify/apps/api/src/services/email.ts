import nodemailer from 'nodemailer';

function getTransport() {
  const host = process.env.SMTP_HOST;
  if (!host) return null;

  return nodemailer.createTransport({
    host,
    port: Number(process.env.SMTP_PORT ?? 587),
    secure: process.env.SMTP_SECURE === 'true',
    auth: process.env.SMTP_USER
      ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS ?? '' }
      : undefined,
  });
}

export async function sendEmail(to: string, subject: string, html: string) {
  const transport = getTransport();
  const from = process.env.SMTP_FROM ?? 'ride@localhost';

  if (!transport) {
    console.log(`[email:mock] To: ${to} | ${subject}`);
    return { sent: true, mock: true };
  }

  await transport.sendMail({ from, to, subject, html });
  return { sent: true, mock: false };
}
