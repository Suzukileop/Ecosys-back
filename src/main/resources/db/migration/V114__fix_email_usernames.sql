-- Fix usernames that were accidentally stored as full emails
-- (User.getUsername() used to return email and polluted API reads).
WITH fixed AS (
    SELECT
        id,
        COALESCE(
            NULLIF(
                LEFT(
                    REGEXP_REPLACE(LOWER(SPLIT_PART(username, '@', 1)), '[^a-z0-9_]', '', 'g'),
                    24
                ),
                ''
            ),
            'user'
        ) AS base
    FROM users
    WHERE username LIKE '%@%'
),
ranked AS (
    SELECT
        f.id,
        f.base,
        ROW_NUMBER() OVER (PARTITION BY f.base ORDER BY f.id) AS rn
    FROM fixed f
)
UPDATE users u
SET username = CASE
    WHEN r.rn = 1
         AND LENGTH(r.base) >= 3
         AND NOT EXISTS (
             SELECT 1
             FROM users o
             WHERE o.deleted_at IS NULL
               AND o.id <> u.id
               AND o.username = r.base
         )
        THEN r.base
    ELSE LEFT(r.base, 20) || '_' || LEFT(REPLACE(u.id::text, '-', ''), 8)
END
FROM ranked r
WHERE u.id = r.id;
