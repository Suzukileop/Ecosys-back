-- Convert all ROLE_CLIENT accounts to ROLE_CREATOR and ensure creator profiles exist.
-- ROLE_CLIENT remains in the roles catalog for historical references but is no longer assigned.

-- Grant ROLE_CREATOR to users who currently have ROLE_CLIENT but not ROLE_CREATOR
INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT ur.user_id, creator_role.id
FROM user_roles ur
JOIN roles client_role ON client_role.id = ur.role_id AND client_role.name = 'ROLE_CLIENT'
CROSS JOIN roles creator_role
WHERE creator_role.name = 'ROLE_CREATOR'
  AND NOT EXISTS (
    SELECT 1
    FROM user_roles ur2
    JOIN roles r2 ON r2.id = ur2.role_id
    WHERE ur2.user_id = ur.user_id
      AND r2.name = 'ROLE_CREATOR'
  );

-- Remove ROLE_CLIENT assignments
DELETE FROM user_roles
WHERE role_id = (SELECT id FROM roles WHERE name = 'ROLE_CLIENT');

-- Create missing creator profiles for ROLE_CREATOR users
INSERT INTO creator_profiles (user_id, created_at)
SELECT u.id, NOW()
FROM users u
WHERE EXISTS (
    SELECT 1
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.name = 'ROLE_CREATOR'
)
AND NOT EXISTS (
    SELECT 1 FROM creator_profiles cp WHERE cp.user_id = u.id
);
