-- Dayparting + screen rotation
ALTER TABLE screens
  ADD COLUMN IF NOT EXISTS rotation_deg INT NOT NULL DEFAULT 0
    CHECK (rotation_deg IN (0, 90, 180, 270));

ALTER TABLE screen_playlists
  ADD COLUMN IF NOT EXISTS daypart_start TIME,
  ADD COLUMN IF NOT EXISTS daypart_end TIME;

COMMENT ON COLUMN screen_playlists.daypart_start IS 'Daily window start (local server time); NULL = all day';
COMMENT ON COLUMN screen_playlists.daypart_end IS 'Daily window end (local server time); NULL = all day';
