-- Merge Job Seeker into Service Provider (Freelancer / Student stays its own role).
UPDATE creator_profiles
SET app_role = 'SERVICE_PROVIDER'
WHERE UPPER(TRIM(app_role)) IN ('JOB_SEEKER');
