package com.plateforme.user.dto;

import jakarta.validation.constraints.Size;

/** Body of POST /api/user/account/password/code — only the language of the e-mail, optional. */
public record PasswordSetupCodeRequest(
        @Size(max = 16)
        String locale
) {}
