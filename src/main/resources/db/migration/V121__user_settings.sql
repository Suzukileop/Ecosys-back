-- Per-user notification and privacy preferences (one row per user, created lazily).
CREATE TABLE IF NOT EXISTS user_settings (
    user_id                  UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_notifications      BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_messages          BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_comments          BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_followers         BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_profile_visits    BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_sales             BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_following_activity BOOLEAN    NOT NULL DEFAULT TRUE,
    show_online_status       BOOLEAN     NOT NULL DEFAULT TRUE,
    private_profile_views    BOOLEAN     NOT NULL DEFAULT FALSE,
    message_permission       VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    created_at               TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_settings_message_permission
        CHECK (message_permission IN ('EVERYONE', 'FOLLOWING', 'NOBODY'))
);
