package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.*;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.service.MarketplaceSocialService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Marketplace Social", description = "Reactions, favorites, comments, reports, shares")
public class MarketplaceSocialController {

    private final MarketplaceSocialService socialService;

    @Operation(summary = "Add or update reaction")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reaction saved"),
            @ApiResponse(responseCode = "404", description = "Target not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/reactions")
    public ResponseEntity<Void> addReaction(@Valid @RequestBody SocialTargetRequest request) {
        socialService.addReaction(getCurrentUserId(), request.targetType(), request.targetId(), request.type());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove reaction")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/social/reactions")
    public ResponseEntity<Void> removeReaction(
            @RequestParam ContentTargetType targetType,
            @RequestParam UUID targetId) {
        socialService.removeReaction(getCurrentUserId(), targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get reaction counts for a target")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Counts returned"),
            @ApiResponse(responseCode = "404", description = "Target not found")
    })
    @GetMapping("/social/reactions/counts")
    public ResponseEntity<ReactionCountsResponse> getReactionCounts(
            @RequestParam ContentTargetType targetType,
            @RequestParam UUID targetId,
            Authentication authentication) {
        return ResponseEntity.ok(socialService.getReactionCounts(
                targetType, targetId, resolveUserId(authentication)));
    }

    @Operation(summary = "List my liked target ids")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/social/reactions/me/ids")
    public ResponseEntity<java.util.List<UUID>> getMyLikedTargetIds(
            @RequestParam ContentTargetType targetType) {
        return ResponseEntity.ok(socialService.getMyLikedTargetIds(getCurrentUserId(), targetType));
    }

    @Operation(summary = "Add favorite")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/favorites")
    public ResponseEntity<Void> addFavorite(@Valid @RequestBody SocialTargetRequest request) {
        socialService.addFavorite(getCurrentUserId(), request.targetType(), request.targetId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove favorite")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/social/favorites")
    public ResponseEntity<Void> removeFavorite(
            @RequestParam ContentTargetType targetType,
            @RequestParam UUID targetId) {
        socialService.removeFavorite(getCurrentUserId(), targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List my favorite target ids")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/favorites/me/ids")
    public ResponseEntity<java.util.List<UUID>> getMyFavoriteTargetIds(
            @RequestParam ContentTargetType targetType) {
        return ResponseEntity.ok(socialService.getMyFavoriteTargetIds(getCurrentUserId(), targetType));
    }

    @Operation(summary = "List my favorites")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/favorites/me")
    public ResponseEntity<PagedResponse<FavoriteResponse>> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                socialService.getMyFavorites(getCurrentUserId(), pageable)));
    }

    @Operation(summary = "List comments for a target")
    @GetMapping("/social/comments")
    public ResponseEntity<PagedResponse<CommentResponse>> getComments(
            @RequestParam ContentTargetType targetType,
            @RequestParam UUID targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                socialService.getComments(
                        targetType, targetId, pageable, resolveUserId(authentication), includeHidden)));
    }

    @Operation(summary = "Post a comment")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/comments")
    public ResponseEntity<CommentResponse> addComment(@Valid @RequestBody CommentRequest request) {
        CommentResponse body = socialService.addComment(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Delete a comment")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/social/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
        socialService.deleteComment(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hide a comment (post creator)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/comments/{id}/hide")
    public ResponseEntity<Void> hideComment(@PathVariable UUID id) {
        socialService.hideComment(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unhide a comment (post creator)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/comments/{id}/unhide")
    public ResponseEntity<Void> unhideComment(@PathVariable UUID id) {
        socialService.unhideComment(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Submit content report")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/reports")
    public ResponseEntity<ReportResponse> submitReport(@Valid @RequestBody ReportRequest request) {
        ReportResponse body = socialService.submitReport(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Record a share event")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/social/shares")
    public ResponseEntity<Void> recordShare(@Valid @RequestBody ShareRequest request) {
        socialService.recordShare(getCurrentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }

    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user.getId();
    }
}
