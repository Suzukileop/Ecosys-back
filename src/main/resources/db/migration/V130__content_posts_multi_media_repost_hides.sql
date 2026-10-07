-- Multi-image posts: `media_url` stays the cover (first image) so every existing reader keeps working;
-- `media_urls` is the full ordered gallery and is empty for single-media / text posts.
ALTER TABLE content_posts
    ADD COLUMN IF NOT EXISTS media_urls JSONB NOT NULL DEFAULT '[]';

-- Repost: a post that points at an original. Deleting the original for good removes its reposts.
ALTER TABLE content_posts
    ADD COLUMN IF NOT EXISTS repost_of_id UUID REFERENCES content_posts(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_content_posts_repost_of_id
    ON content_posts (repost_of_id) WHERE repost_of_id IS NOT NULL;

-- A creator reposts a given post once; undoing a repost is a soft delete, so it frees the slot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_content_posts_one_repost_per_creator
    ON content_posts (creator_id, repost_of_id)
    WHERE repost_of_id IS NOT NULL AND deleted_at IS NULL;

-- "Not interested": posts a viewer asked to stop seeing in their own feed.
CREATE TABLE IF NOT EXISTS content_post_hides (
    user_id    UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id    UUID      NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_content_post_hides_post_id ON content_post_hides (post_id);
