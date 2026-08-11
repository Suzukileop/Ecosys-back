ALTER TABLE content_posts
    ALTER COLUMN media_url DROP NOT NULL;

ALTER TABLE content_posts
    ADD COLUMN media_type VARCHAR(20) NOT NULL DEFAULT 'FILE',
    ADD COLUMN text_color VARCHAR(20),
    ADD COLUMN mood_label VARCHAR(100),
    ADD COLUMN mood_emoji VARCHAR(20),
    ADD COLUMN tagged_user_ids JSONB NOT NULL DEFAULT '[]';
