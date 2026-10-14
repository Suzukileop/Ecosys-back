-- Local sign-ups now have to confirm their e-mail before they can sign in. Accounts that
-- existed before that rule never had the chance, so they are treated as verified rather than
-- locked out on their next login.
UPDATE users
SET email_verified = TRUE,
    email_verified_at = COALESCE(email_verified_at, created_at, NOW())
WHERE email_verified IS NOT TRUE;
