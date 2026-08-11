ALTER TABLE video_segments
    ADD COLUMN video_generating BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN video_generation_error TEXT,
    ADD COLUMN video_duration_seconds INTEGER;
