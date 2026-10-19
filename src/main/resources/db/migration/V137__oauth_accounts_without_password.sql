-- Accounts created through Google were given a random password nobody knows, so Settings › Security
-- asked them for a "current password" they could never type. They now have no password at all and
-- are offered "Create a password" (with an e-mailed code) or "Forgot password?" instead.
--
-- A provider account's hash can't be told apart from a real one: an account that signed up with a
-- password and later linked Google loses that password here too. It isn't locked out — it still
-- signs in with Google and can choose a new password the same two ways.
UPDATE users
SET password_hash = NULL
WHERE auth_provider <> 'LOCAL'
  AND password_hash IS NOT NULL;
