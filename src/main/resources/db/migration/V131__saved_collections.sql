-- Saved posts can be organised into named collections (Settings > Saved posts).
-- A collection only ever holds posts the user has saved: adding to a collection saves the post,
-- un-saving it removes it from every collection of that user (service-level rule).

CREATE TABLE IF NOT EXISTS saved_collections (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(60)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- One name per user, case-insensitive ("Design" and "design" are the same collection).
CREATE UNIQUE INDEX IF NOT EXISTS uq_saved_collections_user_name
    ON saved_collections (user_id, lower(name));

CREATE INDEX IF NOT EXISTS idx_saved_collections_user_updated
    ON saved_collections (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS saved_collection_items (
    id            UUID      PRIMARY KEY,
    collection_id UUID      NOT NULL REFERENCES saved_collections(id) ON DELETE CASCADE,
    post_id       UUID      NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    added_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_saved_collection_items UNIQUE (collection_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_collection_items_post_id
    ON saved_collection_items (post_id);

CREATE INDEX IF NOT EXISTS idx_saved_collection_items_collection_added
    ON saved_collection_items (collection_id, added_at DESC);
