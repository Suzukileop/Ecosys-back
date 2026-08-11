ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS contact_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS contact_phones JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS contact_emails JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Backfill one-item arrays from legacy scalar columns when lists are still empty
UPDATE creator_profiles
SET contact_addresses = jsonb_build_array(
        jsonb_build_object(
                'id', gen_random_uuid()::text,
                'sortOrder', 0,
                'value', btrim(contact_address)
        )
)
WHERE contact_address IS NOT NULL
  AND btrim(contact_address) <> ''
  AND jsonb_array_length(COALESCE(contact_addresses, '[]'::jsonb)) = 0;

UPDATE creator_profiles
SET contact_phones = jsonb_build_array(
        jsonb_build_object(
                'id', gen_random_uuid()::text,
                'sortOrder', 0,
                'value', btrim(contact_phone)
        )
)
WHERE contact_phone IS NOT NULL
  AND btrim(contact_phone) <> ''
  AND jsonb_array_length(COALESCE(contact_phones, '[]'::jsonb)) = 0;

UPDATE creator_profiles
SET contact_emails = jsonb_build_array(
        jsonb_build_object(
                'id', gen_random_uuid()::text,
                'sortOrder', 0,
                'value', btrim(contact_email)
        )
)
WHERE contact_email IS NOT NULL
  AND btrim(contact_email) <> ''
  AND jsonb_array_length(COALESCE(contact_emails, '[]'::jsonb)) = 0;
