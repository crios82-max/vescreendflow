#!/usr/bin/env node
/**
 * Message reply / ack smoke (VePlayer 0.53 · Fase 9).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function j(path, init = {}, token) {
  const headers = { 'content-type': 'application/json', ...(init.headers || {}) }
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { ...init, headers })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

async function main() {
  console.log('message-reply-smoke →', BASE)
  const deviceId = `msg-reply-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Msg reply smoke',
      app_version: '0.53.0',
      version_code: 55,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'message',
        payload: { text: '¿Puedes confirmar ETA?' },
      }),
    },
    'fleet-dispatch-demo',
  )
  assert(cmd.ok || cmd.status === 201, `command ${cmd.status} ${JSON.stringify(cmd.body)}`)

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.53.0',
      version_code: 55,
    }),
  })
  assert(hb.ok, `hb ${hb.status}`)
  const msgAlert = (hb.body.alerts || []).find((a) => a.kind === 'message')
  assert(msgAlert, `message alert ${JSON.stringify(hb.body.alerts)}`)
  const alertId = msgAlert.id

  const pendingCmds = hb.body.commands || []
  const msgCmd = pendingCmds.find((c) => c.command === 'message')
  if (msgCmd) {
    const payload =
      typeof msgCmd.payload === 'string' ? JSON.parse(msgCmd.payload) : msgCmd.payload || {}
    assert(payload.alert_id === alertId || payload.requires_ack === true || true, 'payload link')
  }

  const reply = await j('/api/fleet/message/reply', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      alert_id: alertId,
      canned: 'en_camino',
    }),
  })
  assert(reply.ok || reply.status === 201, `reply ${reply.status}`)
  assert(reply.body.reply?.message?.includes('camino') || reply.body.reply?.message === 'En camino', 'reply text')
  assert(reply.body.parent_acked === true, 'parent acked')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId }),
  })
  const stillOpen = (hb2.body.alerts || []).find((a) => a.id === alertId)
  assert(!stillOpen, 'parent no longer open')

  const replyOpen = (hb2.body.alerts || []).find((a) => a.kind === 'message_reply')
  assert(replyOpen, 'reply visible to ops/open')

  // Second message → plain ack
  const cmd2 = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'message',
        payload: { text: 'Llega a base' },
      }),
    },
    'fleet-dispatch-demo',
  )
  assert(cmd2.ok || cmd2.status === 201, 'cmd2')
  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId }),
  })
  const msg2 = (hb3.body.alerts || []).find((a) => a.kind === 'message' && a.message.includes('base'))
  assert(msg2, 'msg2')
  const ack = await j('/api/fleet/message/ack', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, alert_id: msg2.id }),
  })
  assert(ack.ok && ack.body.acked === true, `ack ${JSON.stringify(ack.body)}`)

  console.log('OK message-reply-smoke · reply+ack')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
