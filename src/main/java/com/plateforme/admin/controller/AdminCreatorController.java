package com.plateforme.admin.controller;

import com.plateforme.admin.dto.UpdateCreatorVerifiedRequest;
import com.plateforme.admin.service.CreatorAdminService;
import com.plateforme.user.dto.CreatorProfileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/creators")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Creators", description = "Creator administration")
@SecurityRequirement(name = "bearerAuth")
public class AdminCreatorController {

    private final CreatorAdminService creatorAdminService;

    @Operation(summary = "Set creator verified status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verified status updated"),
            @ApiResponse(responseCode = "404", description = "Creator not found"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @PatchMapping("/{id}/verified")
    public ResponseEntity<CreatorProfileDto> updateVerified(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCreatorVerifiedRequest request) {
        return ResponseEntity.ok(creatorAdminService.setVerified(id, request.verified()));
    }
}
