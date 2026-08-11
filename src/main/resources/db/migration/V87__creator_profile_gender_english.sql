-- Normalize gender values to English across stored profiles.
UPDATE creator_profiles
SET gender = CASE
    WHEN LOWER(TRIM(gender)) IN ('homme', 'man', 'male', 'm') THEN 'Male'
    WHEN LOWER(TRIM(gender)) IN ('femme', 'woman', 'female', 'f') THEN 'Female'
    ELSE gender
END
WHERE gender IS NOT NULL
  AND TRIM(gender) <> ''
  AND gender NOT IN ('Male', 'Female');
