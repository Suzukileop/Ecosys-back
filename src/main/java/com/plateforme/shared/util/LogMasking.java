package com.plateforme.shared.util;

/** Keeps personal data out of application logs while leaving enough to correlate an incident. */
public final class LogMasking {

    private LogMasking() {
    }

    /** {@code jane.doe@gmail.com} → {@code j***@gmail.com}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
