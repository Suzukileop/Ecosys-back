-- Créneaux (jour 0–6 + heure HH:mm) ; plusieurs créneaux le même jour autorisés.
ALTER TABLE scheduled_configs
    ADD COLUMN publication_slots jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE scheduled_configs c
SET publication_slots = coalesce(
        (
            SELECT jsonb_agg(
                           jsonb_build_object(
                                   'dayOfWeek', (t.v #>> '{}')::int,
                                   'time', c.publication_time
                           )
                           ORDER BY (t.v #>> '{}')::int
                   )
            FROM jsonb_array_elements(c.publication_days) AS t(v)
        ),
        '[]'::jsonb
                          )
WHERE c.publication_days IS NOT NULL
  AND jsonb_typeof(c.publication_days) = 'array'
  AND jsonb_array_length(c.publication_days) > 0;

UPDATE scheduled_configs
SET publication_slots = '[{"dayOfWeek":1,"time":"09:00"}]'::jsonb
WHERE publication_slots IS NULL
   OR publication_slots = '[]'::jsonb;

ALTER TABLE scheduled_configs
    DROP COLUMN publication_days;

ALTER TABLE scheduled_configs
    DROP COLUMN publication_time;
