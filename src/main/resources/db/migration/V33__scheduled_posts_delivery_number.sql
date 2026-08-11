ALTER TABLE scheduled_posts
    ADD COLUMN delivery_number INTEGER;

-- Numérotation des livraisons agent existantes (ordre chronologique par niche)
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY niche_request_id
               ORDER BY COALESCE(published_at, created_at) ASC
           ) AS rn
    FROM scheduled_posts
    WHERE niche_request_id IS NOT NULL
      AND content_type = 'UPLOADED'
)
UPDATE scheduled_posts sp
SET delivery_number = ranked.rn
FROM ranked
WHERE sp.id = ranked.id;

CREATE INDEX idx_scheduled_posts_niche_delivery
    ON scheduled_posts (niche_request_id, delivery_number);
