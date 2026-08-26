/**
 * Seed demo pings around Caracas (Plaza Venezuela area) for local map testing.
 */
import { db } from './db.js'
import { encodeGeohash } from './geohash.js'

const CENTER = { lat: 10.496, lng: -66.898 }
const now = Math.floor(Date.now() / 1000)

db.prepare('DELETE FROM pings').run()

const insert = db.prepare(`
  INSERT INTO pings (lat, lng, accuracy_m, speed_mps, activity, geohash, device_bucket, ts)
  VALUES (@lat, @lng, @accuracy_m, @speed_mps, @activity, @geohash, @device_bucket, @ts)
`)

const tx = db.transaction(() => {
  // Vehicle traffic corridor (east-west) with mixed speeds
  for (let i = 0; i < 40; i++) {
    const lat = CENTER.lat + (Math.random() - 0.5) * 0.008
    const lng = CENTER.lng - 0.02 + i * 0.001 + (Math.random() - 0.5) * 0.0005
    const slow = i > 15 && i < 25
    const speed = slow ? 2 + Math.random() * 4 : 12 + Math.random() * 10
    insert.run({
      lat,
      lng,
      accuracy_m: 8 + Math.random() * 10,
      speed_mps: speed,
      activity: 'IN_VEHICLE',
      geohash: encodeGeohash(lat, lng, 7),
      device_bucket: `veh_${String(i % 12).padStart(2, '0')}_day`,
      ts: now - Math.floor(Math.random() * 600),
    })
  }

  // Pedestrian / still clusters (plaza + mall-ish spots)
  const hubs = [
    { lat: 10.4965, lng: -66.8982, n: 25 },
    { lat: 10.492, lng: -66.879, n: 18 },
    { lat: 10.505, lng: -66.91, n: 12 },
  ]
  let p = 0
  for (const hub of hubs) {
    for (let i = 0; i < hub.n; i++) {
      const lat = hub.lat + (Math.random() - 0.5) * 0.004
      const lng = hub.lng + (Math.random() - 0.5) * 0.004
      const activity = Math.random() > 0.4 ? 'ON_FOOT' : 'STILL'
      insert.run({
        lat,
        lng,
        accuracy_m: 12 + Math.random() * 15,
        speed_mps: activity === 'ON_FOOT' ? 1 + Math.random() : 0,
        activity,
        geohash: encodeGeohash(lat, lng, 7),
        device_bucket: `ped_${String(p % 20).padStart(2, '0')}_day`,
        ts: now - Math.floor(Math.random() * 900),
      })
      p++
    }
  }

  // Tight surround ring around ego for left-panel viz (personas / motos / autos)
  for (let i = 0; i < 18; i++) {
    const ang = (i / 18) * Math.PI * 2
    const distM = 12 + (i % 5) * 6
    const dLat = (Math.cos(ang) * distM) / 111320
    const dLng = (Math.sin(ang) * distM) / (111320 * Math.cos((CENTER.lat * Math.PI) / 180))
    const lat = CENTER.lat + dLat
    const lng = CENTER.lng + dLng
    const kindRoll = i % 3
    const activity = kindRoll === 0 ? 'ON_FOOT' : 'IN_VEHICLE'
    const speed = kindRoll === 0 ? 1.2 : kindRoll === 1 ? 12 : 22
    insert.run({
      lat,
      lng,
      accuracy_m: 6,
      speed_mps: speed,
      activity,
      geohash: encodeGeohash(lat, lng, 7),
      device_bucket: `surr_${String(i).padStart(2, '0')}_day`,
      ts: now - Math.floor(Math.random() * 120),
    })
  }
})

tx()
const count = db.prepare('SELECT COUNT(*) AS n FROM pings').get() as { n: number }
console.log(`Seeded ${count.n} pings around Caracas demo area`)
