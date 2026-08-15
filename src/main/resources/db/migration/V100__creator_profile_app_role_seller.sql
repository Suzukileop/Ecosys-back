-- Allow Seller app role (and keep Job Seeker for legacy rows until normalized).
ALTER TABLE creator_profiles
    DROP CONSTRAINT IF EXISTS creator_profiles_app_role_check;

ALTER TABLE creator_profiles
    ADD CONSTRAINT creator_profiles_app_role_check
    CHECK (app_role IN (
        'GENERAL_MEMBER',
        'SERVICE_PROVIDER',
        'FREELANCER_STUDENT',
        'JOB_SEEKER',
        'SELLER',
        'RH_RECRUITER'
    ));
