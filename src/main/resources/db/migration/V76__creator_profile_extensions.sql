-- About & profile extensions
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS pronouns VARCHAR(50),
    ADD COLUMN IF NOT EXISTS spoken_languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS profile_services JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS faq_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS profile_links JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS avg_response_time_seconds INT,
    ADD COLUMN IF NOT EXISTS response_time_sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS response_time_computed_at TIMESTAMP;

-- Portfolio curation join table
CREATE TABLE IF NOT EXISTS creator_portfolio_posts (
    creator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    added_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (creator_user_id, content_post_id)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_creator_sort
    ON creator_portfolio_posts (creator_user_id, sort_order);

-- Migrate legacy languages string -> spoken_languages JSONB array
UPDATE creator_profiles cp
SET spoken_languages = sub.langs
FROM (
    SELECT cp2.id,
           COALESCE(
               jsonb_agg(to_jsonb(trim(part)) ORDER BY ord),
               '[]'::jsonb
           ) AS langs
    FROM creator_profiles cp2
    CROSS JOIN LATERAL unnest(string_to_array(cp2.languages, ',')) WITH ORDINALITY AS t(part, ord)
    WHERE cp2.languages IS NOT NULL
      AND btrim(cp2.languages) <> ''
      AND btrim(part) <> ''
    GROUP BY cp2.id
) sub
WHERE cp.id = sub.id;

-- Migrate website_url, cta, social_links -> profile_links JSONB
UPDATE creator_profiles cp
SET profile_links = COALESCE(links.merged, '[]'::jsonb)
FROM (
    SELECT cp2.id,
           (
               COALESCE(website.links, '[]'::jsonb)
               || COALESCE(cta.links, '[]'::jsonb)
               || COALESCE(social_array.links, '[]'::jsonb)
               || COALESCE(social_object.links, '[]'::jsonb)
           ) AS merged
    FROM creator_profiles cp2
    LEFT JOIN LATERAL (
        SELECT jsonb_build_array(
            jsonb_build_object(
                'id', gen_random_uuid()::text,
                'type', 'WEBSITE',
                'label', 'Site web',
                'url', btrim(cp2.website_url),
                'sortOrder', 0,
                'platform', NULL
            )
        ) AS links
        WHERE cp2.website_url IS NOT NULL AND btrim(cp2.website_url) <> ''
    ) website ON TRUE
    LEFT JOIN LATERAL (
        SELECT jsonb_build_array(
            jsonb_build_object(
                'id', gen_random_uuid()::text,
                'type', 'CTA',
                'label', COALESCE(NULLIF(btrim(cp2.cta_label), ''), 'Lien principal'),
                'url', btrim(cp2.cta_url),
                'sortOrder', 1,
                'platform', NULL
            )
        ) AS links
        WHERE cp2.cta_url IS NOT NULL AND btrim(cp2.cta_url) <> ''
    ) cta ON TRUE
    LEFT JOIN LATERAL (
        SELECT jsonb_agg(
            jsonb_build_object(
                'id', gen_random_uuid()::text,
                'type', 'SOCIAL',
                'label', COALESCE(NULLIF(btrim(elem->>'platform'), ''), NULLIF(btrim(elem->>'name'), ''), 'Social'),
                'url', btrim(elem->>'url'),
                'sortOrder', ord::int + 10,
                'platform', COALESCE(NULLIF(btrim(elem->>'platform'), ''), NULLIF(btrim(elem->>'name'), ''))
            )
            ORDER BY ord
        ) AS links
        FROM jsonb_array_elements(cp2.social_links) WITH ORDINALITY AS t(elem, ord)
        WHERE cp2.social_links IS NOT NULL
          AND jsonb_typeof(cp2.social_links) = 'array'
          AND btrim(elem->>'url') <> ''
    ) social_array ON TRUE
    LEFT JOIN LATERAL (
        SELECT jsonb_agg(
            jsonb_build_object(
                'id', gen_random_uuid()::text,
                'type', 'SOCIAL',
                'label', kv_key,
                'url', CASE
                    WHEN jsonb_typeof(kv_value) = 'string' THEN btrim(trim(both '"' from kv_value::text))
                    ELSE btrim(kv_value->>'url')
                END,
                'sortOrder', ord::int + 10,
                'platform', kv_key
            )
            ORDER BY ord
        ) AS links
        FROM jsonb_each(cp2.social_links) WITH ORDINALITY AS t(kv_key, kv_value, ord)
        WHERE cp2.social_links IS NOT NULL
          AND jsonb_typeof(cp2.social_links) = 'object'
          AND (
            CASE
              WHEN jsonb_typeof(kv_value) = 'string' THEN btrim(trim(both '"' from kv_value::text))
              ELSE btrim(kv_value->>'url')
            END
          ) <> ''
    ) social_object ON TRUE
) links
WHERE cp.id = links.id
  AND jsonb_array_length(COALESCE(links.merged, '[]'::jsonb)) > 0
  AND jsonb_array_length(COALESCE(cp.profile_links, '[]'::jsonb)) = 0;
