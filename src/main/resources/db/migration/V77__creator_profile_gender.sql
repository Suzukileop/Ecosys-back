ALTER TABLE creator_profiles RENAME COLUMN pronouns TO gender;

UPDATE creator_profiles
SET contact_visibility = (contact_visibility - 'pronouns') || jsonb_build_object('gender', contact_visibility->'pronouns')
WHERE contact_visibility IS NOT NULL
  AND contact_visibility ? 'pronouns';

UPDATE creator_profiles
SET gender = CASE
    WHEN LOWER(TRIM(gender)) IN ('homme', 'man', 'male', 'm') THEN 'Homme'
    WHEN LOWER(TRIM(gender)) IN ('femme', 'woman', 'female', 'f') THEN 'Femme'
    ELSE NULL
END
WHERE gender IS NOT NULL
  AND gender NOT IN ('Homme', 'Femme');
