package com.plateforme.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRoleRequest(
        @NotEmpty(message = "Au moins un rôle est requis")
        Set<String> roles
) {}
