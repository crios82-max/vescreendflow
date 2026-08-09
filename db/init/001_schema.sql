-- vescreenflow initial schema
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  company_name  TEXT,
  logo_url      TEXT,
  plan          TEXT NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'growth', 'enterprise')),
  free_screens  INT NOT NULL DEFAULT 10,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE screens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  pair_code     TEXT NOT NULL UNIQUE,
  status        TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
  location      TEXT,
  last_seen_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE playlists (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE media_assets (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  media_type    TEXT NOT NULL CHECK (media_type IN ('image', 'video')),
  url           TEXT NOT NULL,
  duration_sec  INT NOT NULL DEFAULT 10,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE playlist_items (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  playlist_id   UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  media_id      UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
  sort_order    INT NOT NULL DEFAULT 0,
  duration_sec  INT,
  UNIQUE (playlist_id, media_id, sort_order)
);

CREATE TABLE screen_playlists (
  screen_id     UUID NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
  playlist_id   UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  starts_at     TIMESTAMPTZ,
  ends_at       TIMESTAMPTZ,
  PRIMARY KEY (screen_id, playlist_id)
);

CREATE INDEX idx_screens_user ON screens(user_id);
CREATE INDEX idx_playlists_user ON playlists(user_id);
CREATE INDEX idx_media_user ON media_assets(user_id);
CREATE INDEX idx_playlist_items_playlist ON playlist_items(playlist_id);

-- Demo seed (password: password123)
INSERT INTO users (email, password_hash, full_name, company_name, plan)
VALUES (
  'demo@vescreenflow.com',
  '$2b$10$v5WkOR4ZDbXD/lnwFkKvGuFnUZT/LItQ28GTEpgTSQhhI72vZs8Jq',
  'Demo User',
  'vescreenflow Demo',
  'free'
);

INSERT INTO playlists (user_id, name)
SELECT id, 'Welcome Loop' FROM users WHERE email = 'demo@vescreenflow.com';

INSERT INTO playlists (user_id, name)
SELECT id, 'Lunch Specials' FROM users WHERE email = 'demo@vescreenflow.com';

INSERT INTO screens (user_id, name, pair_code, status, location)
SELECT id, 'Lobby TV', '48291037', 'online', 'Front lobby'
FROM users WHERE email = 'demo@vescreenflow.com';

INSERT INTO screens (user_id, name, pair_code, status, location)
SELECT id, 'Menu Board A', '19384756', 'online', 'Kitchen wall'
FROM users WHERE email = 'demo@vescreenflow.com';

INSERT INTO screen_playlists (screen_id, playlist_id)
SELECT s.id, p.id
FROM screens s
JOIN users u ON u.id = s.user_id
JOIN playlists p ON p.user_id = u.id AND p.name = 'Welcome Loop'
WHERE s.name = 'Lobby TV';

INSERT INTO screen_playlists (screen_id, playlist_id)
SELECT s.id, p.id
FROM screens s
JOIN users u ON u.id = s.user_id
JOIN playlists p ON p.user_id = u.id AND p.name = 'Lunch Specials'
WHERE s.name = 'Menu Board A';
