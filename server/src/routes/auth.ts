import { Router } from 'express'
import bcrypt from 'bcryptjs'
import { z } from 'zod'
import { query } from '../db/pool.js'
import { signToken } from '../middleware/auth.js'

export const authRouter = Router()

const signupSchema = z.object({
  name: z.string().min(2),
  email: z.string().email(),
  password: z.string().min(6),
})

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
})

authRouter.post('/signup', async (req, res) => {
  const parsed = signupSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de registro inválidos' })
  }

  const { name, email, password } = parsed.data
  const existing = await query('SELECT id FROM users WHERE email = $1', [
    email.toLowerCase(),
  ])
  if (existing.rowCount) {
    return res.status(409).json({ error: 'Este correo ya está registrado' })
  }

  const passwordHash = await bcrypt.hash(password, 10)
  const result = await query<{
    id: string
    email: string
    full_name: string
  }>(
    `INSERT INTO users (email, password_hash, full_name)
     VALUES ($1, $2, $3)
     RETURNING id, email, full_name`,
    [email.toLowerCase(), passwordHash, name],
  )

  const user = result.rows[0]
  const token = signToken({
    id: user.id,
    email: user.email,
    fullName: user.full_name,
  })

  return res.status(201).json({
    token,
    user: { id: user.id, email: user.email, name: user.full_name },
  })
})

authRouter.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de inicio de sesión inválidos' })
  }

  const { email, password } = parsed.data
  const result = await query<{
    id: string
    email: string
    full_name: string
    password_hash: string
  }>('SELECT id, email, full_name, password_hash FROM users WHERE email = $1', [
    email.toLowerCase(),
  ])

  const user = result.rows[0]
  if (!user) {
    return res.status(401).json({ error: 'Correo o contraseña incorrectos' })
  }

  const ok = await bcrypt.compare(password, user.password_hash)
  if (!ok) {
    return res.status(401).json({ error: 'Correo o contraseña incorrectos' })
  }

  const token = signToken({
    id: user.id,
    email: user.email,
    fullName: user.full_name,
  })

  return res.json({
    token,
    user: { id: user.id, email: user.email, name: user.full_name },
  })
})
