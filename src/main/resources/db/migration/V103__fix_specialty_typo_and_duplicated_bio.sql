-- Fix recurring demo/profile data issues: specialty typo DEVOOPS → DevOps,
-- and bios that were stored as the same paragraph twice back-to-back.

UPDATE creator_profiles
SET specialties = (
    SELECT COALESCE(
        jsonb_agg(
            CASE
                WHEN lower(trim(elem)) IN ('devoops', 'devops') THEN to_jsonb('DevOps'::text)
                ELSE to_jsonb(trim(elem))
            END
            ORDER BY ordinality
        ),
        '[]'::jsonb
    )
    FROM jsonb_array_elements_text(COALESCE(specialties, '[]'::jsonb))
        WITH ORDINALITY AS t(elem, ordinality)
)
WHERE specialties IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements_text(specialties) AS s(elem)
      WHERE lower(trim(elem)) = 'devoops'
  );

UPDATE creator_profiles
SET specialite = 'DevOps'
WHERE specialite IS NOT NULL
  AND lower(trim(specialite)) IN ('devoops', 'devops')
  AND specialite IS DISTINCT FROM 'DevOps';

UPDATE creator_profiles
SET bio = left(bio, length(bio) / 2)
WHERE bio IS NOT NULL
  AND length(bio) >= 40
  AND length(bio) % 2 = 0
  AND left(bio, length(bio) / 2) = right(bio, length(bio) / 2);
