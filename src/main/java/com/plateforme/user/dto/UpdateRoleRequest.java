package com.plateforme.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRoleRequest(
        @NotEmpty(message = "Select at least one role.")
        Set<String> roles
) {}
