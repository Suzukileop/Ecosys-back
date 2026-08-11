ALTER TABLE content_posts
    ADD COLUMN archived_at TIMESTAMP;

ALTER TABLE content_posts
    ADD COLUMN pinned_at TIMESTAMP;

CREATE INDEX idx_content_posts_creator_archived ON content_posts (creator_id, archived_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_posts_creator_pinned ON content_posts (creator_id, pinned_at DESC)
    WHERE deleted_at IS NULL AND archived_at IS NULL;
