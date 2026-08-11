ALTER TABLE video_analyses
    ADD COLUMN global_text TEXT,
    ADD COLUMN text_variants JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN text_variant_theme TEXT;
