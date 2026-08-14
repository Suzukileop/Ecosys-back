-- App experience role (Information → My Role). Default: GENERAL_MEMBER.
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS app_role VARCHAR(40) NOT NULL DEFAULT 'GENERAL_MEMBER';

ALTER TABLE creator_profiles
    DROP CONSTRAINT IF EXISTS creator_profiles_app_role_check;

ALTER TABLE creator_profiles
    ADD CONSTRAINT creator_profiles_app_role_check
    CHECK (app_role IN (
        'GENERAL_MEMBER',
        'SERVICE_PROVIDER',
        'FREELANCER_STUDENT',
        'JOB_SEEKER',
        'RH_RECRUITER'
    ));
