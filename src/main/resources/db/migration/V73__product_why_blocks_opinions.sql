-- Migrate why_product_blocks from ProfileMediaBlock shape (text, subtitles)
-- to ProductWhyBlock shape (opinions only).
UPDATE marketplace_products
SET why_product_blocks = COALESCE((
    SELECT jsonb_agg(block ORDER BY sort_order)
    FROM (
        SELECT
            COALESCE((elem->>'sortOrder')::int, idx - 1) AS sort_order,
            jsonb_strip_nulls(jsonb_build_object(
                'id', elem->'id',
                'sortOrder', COALESCE((elem->>'sortOrder')::int, idx - 1),
                'mediaUrl', NULLIF(btrim(elem->>'mediaUrl'), ''),
                'mediaType', NULLIF(btrim(elem->>'mediaType'), ''),
                'opinions', (
                    SELECT COALESCE(jsonb_agg(to_jsonb(opinion)), '[]'::jsonb)
                    FROM (
                        SELECT btrim(elem->>'text') AS opinion
                        WHERE elem->>'text' IS NOT NULL
                          AND btrim(elem->>'text') <> ''
                        UNION ALL
                        SELECT btrim(subtitle.value) AS opinion
                        FROM jsonb_array_elements_text(COALESCE(elem->'subtitles', '[]'::jsonb)) AS subtitle(value)
                        WHERE btrim(subtitle.value) <> ''
                    ) opinions_list
                )
            )) AS block
        FROM jsonb_array_elements(why_product_blocks) WITH ORDINALITY AS t(elem, idx)
    ) migrated
), '[]'::jsonb)
WHERE why_product_blocks IS NOT NULL
  AND jsonb_typeof(why_product_blocks) = 'array'
  AND jsonb_array_length(why_product_blocks) > 0;
