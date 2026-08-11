package com.plateforme.user.controller;

import com.plateforme.user.dto.UpdateUserProfileDto;
import com.plateforme.user.dto.UserDto;
import com.plateforme.user.entity.User;
import com.plateforme.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Profile", description = "Profil utilisateur (photo, nom)")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Consulter mon profil")
    @GetMapping
    public ResponseEntity<UserDto> getMyProfile() {
        return ResponseEntity.ok(userProfileService.getMyProfile(getCurrentUserId()));
    }

    @Operation(summary = "Mettre à jour mon profil")
    @PutMapping
    public ResponseEntity<UserDto> updateMyProfile(@Valid @RequestBody UpdateUserProfileDto dto) {
        return ResponseEntity.ok(userProfileService.updateMyProfile(getCurrentUserId(), dto));
    }

    @Operation(summary = "Uploader ma photo de profil")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(userProfileService.uploadAvatar(getCurrentUserId(), file));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
