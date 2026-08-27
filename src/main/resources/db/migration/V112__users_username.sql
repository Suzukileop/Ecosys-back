-- Unique public username (case-insensitive: leopard == Leopard).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(30);

-- Backfill from email local-part; collide with id suffix so LOWER(username) stays unique.
WITH prepared AS (
    SELECT
        u.id,
        COALESCE(
            NULLIF(
                LEFT(
                    REGEXP_REPLACE(LOWER(SPLIT_PART(u.email, '@', 1)), '[^a-z0-9_]', '', 'g'),
                    24
                ),
                ''
            ),
            'user'
        ) AS base
    FROM users u
    WHERE u.username IS NULL
),
ranked AS (
    SELECT
        id,
        base,
        ROW_NUMBER() OVER (PARTITION BY LOWER(base) ORDER BY id) AS rn
    FROM prepared
)
UPDATE users u
SET username = CASE
    WHEN r.rn = 1 AND LENGTH(r.base) >= 3 THEN r.base
    ELSE LEFT(r.base, 20) || '_' || LEFT(REPLACE(u.id::text, '-', ''), 8)
END
FROM ranked r
WHERE u.id = r.id
  AND u.username IS NULL;

-- Ensure every row has a username before enforcing NOT NULL.
UPDATE users
SET username = 'user_' || LEFT(REPLACE(id::text, '-', ''), 8)
WHERE username IS NULL OR BTRIM(username) = '';

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_lower
    ON users (LOWER(username))
    WHERE deleted_at IS NULL;
