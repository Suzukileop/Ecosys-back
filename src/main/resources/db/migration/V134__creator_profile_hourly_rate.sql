ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS hourly_rate_cents INTEGER,
    ADD COLUMN IF NOT EXISTS hourly_rate_currency VARCHAR(8);

ALTER TABLE creator_profiles
    ADD CONSTRAINT creator_profiles_hourly_rate_positive
        CHECK (hourly_rate_cents IS NULL OR hourly_rate_cents > 0);

COMMENT ON COLUMN creator_profiles.hourly_rate_cents IS
    'Provider hourly rate in minor units (cents); null = not set.';
COMMENT ON COLUMN creator_profiles.hourly_rate_currency IS
    'ISO-like currency code of the hourly rate (EUR, USD, MGA…); null when no rate is set.';
