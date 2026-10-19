-- Dev-only: eight creator accounts for parallel testing, so sessions don't sign each other out of
-- creator@noprobleme.com. Not a Flyway migration on purpose — it must never reach production.
--
--   tester1@noprobleme.com … tester8@noprobleme.com  /  Tester123!
--
-- Each one gets a copy of creator@noprobleme.com's creator profile (bio, skills, education,
-- portfolio settings…) so every portfolio section has content to test. Idempotent: existing
-- accounts are left as they are. Run:
--   psql -h localhost -U postgres -d noprobleme -f backend/scripts/seed-test-creators.sql

DO $$
DECLARE
    v_source_user    UUID;
    v_role_creator   UUID;
    v_user           UUID;
    v_email          TEXT;
    v_columns        TEXT;
    i                INT;
BEGIN
    SELECT id INTO v_source_user FROM users WHERE email = 'creator@noprobleme.com';
    SELECT id INTO v_role_creator FROM roles WHERE name = 'ROLE_CREATOR';

    -- Every creator_profiles column except its own identity and counters.
    SELECT string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position)
      INTO v_columns
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'creator_profiles'
       AND column_name NOT IN ('id', 'user_id', 'created_at', 'profile_visits',
                               'avg_response_time_seconds', 'response_time_sample_count',
                               'response_time_computed_at');

    FOR i IN 1..8 LOOP
        v_email := 'tester' || i || '@noprobleme.com';
        IF EXISTS (SELECT 1 FROM users WHERE email = v_email) THEN
            RAISE NOTICE 'Skipped (exists): %', v_email;
            CONTINUE;
        END IF;

        INSERT INTO users (email, password_hash, full_name, username, avatar_url,
                           email_verified, email_verified_at, enabled, account_non_locked, created_at)
        SELECT v_email,
               crypt('Tester123!', gen_salt('bf', 10)),
               'Test Creator ' || i,
               'tester' || i,
               src.avatar_url,
               true, now(), true, true, now()
          FROM users src
         WHERE src.id = v_source_user
        RETURNING id INTO v_user;

        INSERT INTO user_roles (user_id, role_id) VALUES (v_user, v_role_creator);

        IF v_source_user IS NOT NULL AND EXISTS (SELECT 1 FROM creator_profiles WHERE user_id = v_source_user) THEN
            EXECUTE format(
                'INSERT INTO creator_profiles (user_id, created_at, %1$s) '
                'SELECT $1, now(), %1$s FROM creator_profiles WHERE user_id = $2',
                v_columns
            ) USING v_user, v_source_user;
        ELSE
            INSERT INTO creator_profiles (user_id, bio, is_verified, created_at)
            VALUES (v_user, 'Test creator profile.', false, now());
        END IF;

        RAISE NOTICE 'Created: %', v_email;
    END LOOP;
END;
$$;
