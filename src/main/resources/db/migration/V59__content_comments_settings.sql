ALTER TABLE content_posts
    ADD COLUMN comments_enabled BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE content_comments
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN hidden_at TIMESTAMP,
    ADD COLUMN hidden_by UUID REFERENCES users(id);

CREATE INDEX idx_content_comments_visible_target
    ON content_comments (target_type, target_id, created_at DESC)
    WHERE deleted_at IS NULL;
