ALTER TABLE video_segments
    ADD COLUMN audio_suggestions JSONB NOT NULL DEFAULT '[]'::jsonb;
