package com.plateforme.auth.service;

/**
 * What an e-mailed one-time code unlocks. Each purpose has its own Redis keys and its own HMAC input,
 * so a code sent for one action can never be spent on another (e.g. a sign-up code can't set a password).
 */
public enum VerificationCodePurpose {

    /** Confirms the address of a local sign-up; the first session starts once it's checked. */
    EMAIL_VERIFICATION("email-verify", VerificationEmailTemplates.Kind.VERIFY_EMAIL),

    /** A signed-in account without a password (created with Google) proves it owns its inbox before adding one. */
    PASSWORD_SETUP("password-setup", VerificationEmailTemplates.Kind.PASSWORD),

    /** "Forgot password?": proves inbox ownership from the log-in page, then a new password is chosen. */
    PASSWORD_RESET("password-reset", VerificationEmailTemplates.Kind.PASSWORD);

    private final String keyPrefix;
    private final VerificationEmailTemplates.Kind emailKind;

    VerificationCodePurpose(String keyPrefix, VerificationEmailTemplates.Kind emailKind) {
        this.keyPrefix = keyPrefix;
        this.emailKind = emailKind;
    }

    String keyPrefix() {
        return keyPrefix;
    }

    VerificationEmailTemplates.Kind emailKind() {
        return emailKind;
    }
}
