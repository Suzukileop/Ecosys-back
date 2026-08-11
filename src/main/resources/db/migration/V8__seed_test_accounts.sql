-- Seed des comptes de test prédéfinis
-- Utilise pgcrypto pour générer les hashes BCrypt compatibles Spring Security
-- ADMIN  : admin@noprobleme.com  / Admin123!
-- AGENT  : agent@noprobleme.com  / Agent123!

DO $$
DECLARE
    v_admin_id  UUID;
    v_agent_id  UUID;
    v_role_admin_id UUID;
    v_role_agent_id UUID;
BEGIN
    -- Récupérer les IDs des rôles
    SELECT id INTO v_role_admin_id FROM roles WHERE name = 'ROLE_ADMIN';
    SELECT id INTO v_role_agent_id FROM roles WHERE name = 'ROLE_AGENT';

    -- Créer le compte ADMIN (si n'existe pas déjà)
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@noprobleme.com') THEN
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
            'admin@noprobleme.com',
            crypt('Admin123!', gen_salt('bf', 10)),
            'Super Admin',
            true,
            now(),
            true,
            true,
            now()
        ) RETURNING id INTO v_admin_id;

        INSERT INTO user_roles (user_id, role_id)
        VALUES (v_admin_id, v_role_admin_id);

        RAISE NOTICE 'Compte ADMIN créé : admin@noprobleme.com';
    ELSE
        RAISE NOTICE 'Compte ADMIN existe déjà, ignoré.';
    END IF;

    -- Créer le compte AGENT (si n'existe pas déjà)
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'agent@noprobleme.com') THEN
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
            'agent@noprobleme.com',
            crypt('Agent123!', gen_salt('bf', 10)),
            'Agent Test',
            true,
            now(),
            true,
            true,
            now()
        ) RETURNING id INTO v_agent_id;

        INSERT INTO user_roles (user_id, role_id)
        VALUES (v_agent_id, v_role_agent_id);

        RAISE NOTICE 'Compte AGENT créé : agent@noprobleme.com';
    ELSE
        RAISE NOTICE 'Compte AGENT existe déjà, ignoré.';
    END IF;
END;
$$;
