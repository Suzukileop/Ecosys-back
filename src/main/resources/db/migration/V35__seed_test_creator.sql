-- Seed test creator account: creator@noprobleme.com / Creator123!

DO $$
DECLARE
    v_creator_id     UUID;
    v_role_creator_id UUID;
BEGIN
    SELECT id INTO v_role_creator_id FROM roles WHERE name = 'ROLE_CREATOR';

    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'creator@noprobleme.com') THEN
        INSERT INTO users (
            email,
            password_hash,
            full_name,
            email_verified,
            email_verified_at,
            enabled,
            account_non_locked,
            created_at
        ) VALUES (
            'creator@noprobleme.com',
            crypt('Creator123!', gen_salt('bf', 10)),
            'Test Creator',
            true,
            now(),
            true,
            true,
            now()
        ) RETURNING id INTO v_creator_id;

        INSERT INTO user_roles (user_id, role_id)
        VALUES (v_creator_id, v_role_creator_id);

        INSERT INTO creator_profiles (user_id, bio, niche, is_verified, created_at)
        VALUES (
            v_creator_id,
            'Demo creator profile for marketplace development.',
            'Digital Content',
            false,
            now()
        );

        RAISE NOTICE 'Creator account created: creator@noprobleme.com';
    ELSE
        RAISE NOTICE 'Creator account already exists, skipped.';
    END IF;
END;
$$;
