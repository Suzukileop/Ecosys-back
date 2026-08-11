-- Compte CLIENT de test + solde crédits pour le pipeline IA (templates)
-- Email    : client@noprobleme.com
-- Mot de passe : Client123!
-- Solde initial : 1000 crédits (réajustable via PUT /api/admin/users/{id}/credits)

DO $$
DECLARE
    v_client_id       UUID;
    v_role_client_id  UUID;
BEGIN
    SELECT id INTO v_role_client_id FROM roles WHERE name = 'ROLE_CLIENT';

    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'client@noprobleme.com') THEN
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
            'client@noprobleme.com',
            crypt('Client123!', gen_salt('bf', 10)),
            'Client Test IA',
            true,
            now(),
            true,
            true,
            now()
        ) RETURNING id INTO v_client_id;

        INSERT INTO user_roles (user_id, role_id)
        VALUES (v_client_id, v_role_client_id);

        RAISE NOTICE 'Compte CLIENT créé : client@noprobleme.com / Client123!';
    ELSE
        SELECT id INTO v_client_id FROM users WHERE email = 'client@noprobleme.com';
        RAISE NOTICE 'Compte CLIENT existe déjà : client@noprobleme.com';
    END IF;

    INSERT INTO user_credits (user_id, balance, created_at, updated_at)
    VALUES (v_client_id, 1000, now(), now())
    ON CONFLICT (user_id) DO UPDATE
        SET balance = EXCLUDED.balance,
            updated_at = now();

    INSERT INTO credit_transactions (user_id, amount, reason, created_at)
    VALUES (v_client_id, 1000, 'Seed compte test client (V22)', now());
END;
$$;
