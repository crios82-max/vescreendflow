const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz'

/** Encode lat/lng to geohash (precision 1–12). */
export function encodeGeohash(lat: number, lng: number, precision = 7): string {
  let idx = 0
  let bit = 0
  let even = true
  let latMin = -90
  let latMax = 90
  let lngMin = -180
  let lngMax = 180
  let hash = ''

  while (hash.length < precision) {
    if (even) {
      const mid = (lngMin + lngMax) / 2
      if (lng >= mid) {
        idx = (idx << 1) + 1
        lngMin = mid
      } else {
        idx = idx << 1
        lngMax = mid
      }
    } else {
      const mid = (latMin + latMax) / 2
      if (lat >= mid) {
        idx = (idx << 1) + 1
        latMin = mid
      } else {
        idx = idx << 1
        latMax = mid
      }
    }
    even = !even
    bit++
    if (bit === 5) {
      hash += BASE32[idx]
      bit = 0
      idx = 0
    }
  }
  return hash
}

/** Approximate center of a geohash cell. */
export function decodeGeohashCenter(hash: string): { lat: number; lng: number } {
  let even = true
  let latMin = -90
  let latMax = 90
  let lngMin = -180
  let lngMax = 180

  for (const ch of hash) {
    const idx = BASE32.indexOf(ch)
    if (idx < 0) continue
    for (let mask = 16; mask > 0; mask >>= 1) {
      if (even) {
        const mid = (lngMin + lngMax) / 2
        if (idx & mask) lngMin = mid
        else lngMax = mid
      } else {
        const mid = (latMin + latMax) / 2
        if (idx & mask) latMin = mid
        else latMax = mid
      }
      even = !even
    }
  }
  return { lat: (latMin + latMax) / 2, lng: (lngMin + lngMax) / 2 }
}
