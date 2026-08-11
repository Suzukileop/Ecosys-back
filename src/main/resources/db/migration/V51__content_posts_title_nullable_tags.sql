ALTER TABLE content_posts
    ALTER COLUMN title DROP NOT NULL;

ALTER TABLE content_posts
    ADD COLUMN tags JSONB NOT NULL DEFAULT '[]';
