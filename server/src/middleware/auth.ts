import type { Request, Response, NextFunction } from 'express'
import jwt from 'jsonwebtoken'

export type AuthUser = {
  id: string
  email: string
  fullName: string
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser
    }
  }
}

const JWT_SECRET = process.env.JWT_SECRET || 'screenflow-dev-secret-change-me'

export function signToken(user: AuthUser) {
  return jwt.sign(user, JWT_SECRET, { expiresIn: '7d' })
}

export function requireAuth(req: Request, res: Response, next: NextFunction) {
  const header = req.headers.authorization
  if (!header?.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'No autorizado' })
  }

  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET) as AuthUser
    req.user = payload
    return next()
  } catch {
    return res.status(401).json({ error: 'Token inválido o expirado' })
  }
}
