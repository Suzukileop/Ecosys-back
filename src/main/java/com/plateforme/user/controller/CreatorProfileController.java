package com.plateforme.user.controller;

import com.plateforme.user.dto.CreatorProfileDto;
import com.plateforme.user.dto.CreatorProfileImageDto;
import com.plateforme.user.dto.CreatorProfileVisitItemDto;
import com.plateforme.user.dto.CreatorFollowItemDto;
import com.plateforme.user.dto.UpdateCreatorProfileDto;
import com.plateforme.user.dto.UpdatePortfolioRequest;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.user.entity.User;
import com.plateforme.marketplace.service.CreatorProfileViewService;
import com.plateforme.user.service.CreatorPortfolioService;
import com.plateforme.user.service.CreatorProfileImageService;
import com.plateforme.user.service.CreatorProfileService;
import com.plateforme.user.service.CreatorFollowService;
import com.plateforme.shared.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Creator Profile", description = "Gestion du profil créateur de contenu")
public class CreatorProfileController {

    private final CreatorProfileService creatorProfileService;
    private final CreatorPortfolioService creatorPortfolioService;
    private final CreatorProfileViewService creatorProfileViewService;
    private final CreatorFollowService creatorFollowService;
    private final CreatorProfileImageService creatorProfileImageService;

    @Operation(summary = "Consulter mon profil créateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil retourné"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Profil introuvable")
    })
    @GetMapping("/profile")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileDto> getMyProfile() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileService.getMyProfile(userId));
    }

    @Operation(summary = "Mettre à jour mon profil créateur",
            description = "Crée le profil s'il n'existe pas encore, puis le met à jour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @PutMapping("/profile")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileDto> updateMyProfile(
            @Valid @RequestBody UpdateCreatorProfileDto dto) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileService.updateMyProfile(userId, dto));
    }

    @Operation(summary = "Consulter la curation portfolio du créateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posts curatés retournés"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/profile/portfolio")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<ContentPostResponse>> getMyPortfolio() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorPortfolioService.getCuratedPostsForOwner(userId));
    }

    @Operation(summary = "Mettre à jour la curation portfolio (ordre des posts)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portfolio mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @PutMapping("/profile/portfolio")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<ContentPostResponse>> updateMyPortfolio(
            @Valid @RequestBody UpdatePortfolioRequest request) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorPortfolioService.updateCuratedPosts(userId, request));
    }

    @Operation(summary = "Consulter les réglages de présentation du portfolio public")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réglages retournés"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/profile/portfolio-settings")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Map<String, Object>> getMyPortfolioSettings() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileService.getPortfolioSettings(userId));
    }

    @Operation(summary = "Mettre à jour les réglages de présentation du portfolio public")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réglages mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @PutMapping("/profile/portfolio-settings")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Map<String, Object>> updateMyPortfolioSettings(
            @RequestBody Map<String, Object> settings) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileService.updatePortfolioSettings(userId, settings));
    }

    @Operation(summary = "List saved profile photos history")
    @GetMapping("/profile/images")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<CreatorProfileImageDto>> listMyProfileImages() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileImageService.listForCreator(userId));
    }

    @Operation(summary = "Restore a historical profile photo or cover as the current one")
    @PostMapping("/profile/images/{imageId}/restore")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileImageDto> restoreMyProfileImage(@PathVariable UUID imageId) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(creatorProfileImageService.restore(userId, imageId));
    }

    @Operation(summary = "List profile visitors for the authenticated creator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated visitor list"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Profil introuvable")
    })
    @GetMapping("/profile/visits")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PagedResponse<CreatorProfileVisitItemDto>> listMyProfileVisits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "viewedAt"));
        return ResponseEntity.ok(PagedResponse.fromPage(
                creatorProfileViewService.listVisitsForCreator(userId, pageable)));
    }

    @Operation(summary = "List profile subscribers for the authenticated creator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated subscriber list"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Profil introuvable")
    })
    @GetMapping("/profile/followers")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PagedResponse<CreatorFollowItemDto>> listMyProfileFollowers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(PagedResponse.fromPage(
                creatorFollowService.listFollowersForCreator(userId, pageable)));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
