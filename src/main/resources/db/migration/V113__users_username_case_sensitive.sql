-- Username uniqueness is case-sensitive: leopard ≠ Leopard.
DROP INDEX IF EXISTS uq_users_username_lower;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username
    ON users (username)
    WHERE deleted_at IS NULL;
