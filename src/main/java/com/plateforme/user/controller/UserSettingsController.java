package com.plateforme.user.controller;

import com.plateforme.user.dto.AccountSecurityDto;
import com.plateforme.user.dto.ChangePasswordRequest;
import com.plateforme.user.dto.DeleteAccountRequest;
import com.plateforme.user.dto.UpdateUserSettingsDto;
import com.plateforme.user.dto.UserSettingsDto;
import com.plateforme.user.entity.User;
import com.plateforme.user.service.AccountService;
import com.plateforme.user.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "Settings & privacy", description = "Notification, privacy, security and account settings")
@SecurityRequirement(name = "bearerAuth")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;
    private final AccountService accountService;

    @Operation(summary = "Get my notification and privacy settings")
    @GetMapping("/settings")
    public ResponseEntity<UserSettingsDto> getSettings() {
        return ResponseEntity.ok(userSettingsService.getSettings(getCurrentUserId()));
    }

    @Operation(summary = "Update my notification and privacy settings (partial)")
    @PutMapping("/settings")
    public ResponseEntity<UserSettingsDto> updateSettings(@Valid @RequestBody UpdateUserSettingsDto dto) {
        return ResponseEntity.ok(userSettingsService.updateSettings(getCurrentUserId(), dto));
    }

    @Operation(summary = "Account security overview")
    @GetMapping("/account/security")
    public ResponseEntity<AccountSecurityDto> getSecurity() {
        return ResponseEntity.ok(accountService.getSecurity(getCurrentUserId()));
    }

    @Operation(summary = "Change (or set) my password")
    @PostMapping("/account/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(getCurrentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sign out of every device")
    @PostMapping("/account/sessions/revoke-all")
    public ResponseEntity<Map<String, Integer>> revokeAllSessions() {
        return ResponseEntity.ok(Map.of("revoked", accountService.revokeAllSessions(getCurrentUserId())));
    }

    @Operation(summary = "Download a copy of my data (JSON)")
    @GetMapping("/account/export")
    public ResponseEntity<Map<String, Object>> exportData() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"skraft-data.json\"")
                .body(accountService.exportData(getCurrentUserId()));
    }

    @Operation(summary = "Delete my account")
    @PostMapping("/account/delete")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(getCurrentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
