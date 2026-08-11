ALTER TABLE video_analyses
  ADD COLUMN IF NOT EXISTS images_per_segment INT NOT NULL DEFAULT 3
    CHECK (images_per_segment >= 1 AND images_per_segment <= 5);
