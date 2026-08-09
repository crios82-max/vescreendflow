-- Groups + video wall
CREATE TABLE IF NOT EXISTS screen_groups (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  mode          TEXT NOT NULL DEFAULT 'group'
                  CHECK (mode IN ('group', 'videowall')),
  rows          INT NOT NULL DEFAULT 1 CHECK (rows >= 1 AND rows <= 8),
  cols          INT NOT NULL DEFAULT 1 CHECK (cols >= 1 AND cols <= 8),
  playlist_id   UUID REFERENCES playlists(id) ON DELETE SET NULL,
  cycle_epoch   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS screen_group_members (
  group_id      UUID NOT NULL REFERENCES screen_groups(id) ON DELETE CASCADE,
  screen_id     UUID NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
  row_index     INT NOT NULL DEFAULT 0 CHECK (row_index >= 0),
  col_index     INT NOT NULL DEFAULT 0 CHECK (col_index >= 0),
  PRIMARY KEY (group_id, screen_id),
  UNIQUE (screen_id)
);

CREATE INDEX IF NOT EXISTS idx_screen_groups_user ON screen_groups(user_id);
CREATE INDEX IF NOT EXISTS idx_screen_group_members_group ON screen_group_members(group_id);
