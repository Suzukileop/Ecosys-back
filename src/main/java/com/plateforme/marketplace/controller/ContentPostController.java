package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ContentPostBucket;
import com.plateforme.marketplace.dto.ContentPostRequest;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.ContentPostCommentsRequest;
import com.plateforme.marketplace.dto.ContentPostVisibilityRequest;
import com.plateforme.marketplace.dto.MinimalUserDto;
import com.plateforme.marketplace.service.ContentPostService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/creator/content")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Creator Content", description = "Portfolio créateur")
public class ContentPostController {

    private final ContentPostService contentPostService;

    @Operation(summary = "Publier un contenu portfolio")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contenu créé"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> createPost(@Valid @RequestBody ContentPostRequest request) {
        UUID creatorId = getCurrentUserId();
        ContentPostResponse body = contentPostService.createPost(creatorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Lister mes contenus")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PagedResponse<ContentPostResponse>> getMyPosts(
            @RequestParam(defaultValue = "active") String bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID creatorId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        ContentPostBucket resolved = ContentPostBucket.fromParam(bucket);
        return ResponseEntity.ok(PagedResponse.fromPage(contentPostService.getMyPosts(creatorId, resolved, pageable)));
    }

    @Operation(summary = "Obtenir un de mes contenus")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenu trouvé"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Contenu introuvable")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> getMyPost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.getMyPostById(creatorId, id));
    }

    @Operation(summary = "Mettre à jour un contenu")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenu mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Contenu introuvable")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody ContentPostRequest request) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.updatePost(creatorId, id, request));
    }

    @Operation(summary = "Supprimer un contenu (corbeille, soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Suppression effectuée"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "404", description = "Contenu introuvable")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        contentPostService.deletePost(creatorId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> updateVisibility(
            @PathVariable UUID id,
            @Valid @RequestBody ContentPostVisibilityRequest request) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.updateVisibility(creatorId, id, request.isPublic()));
    }

    @PatchMapping("/{id}/comments")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> updateCommentsEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody ContentPostCommentsRequest request) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.updateCommentsEnabled(
                creatorId, id, request.commentsEnabled()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> archivePost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.archivePost(creatorId, id));
    }

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> unarchivePost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.unarchivePost(creatorId, id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> restorePost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.restorePost(creatorId, id));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> permanentDeletePost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        contentPostService.permanentDeletePost(creatorId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> pinPost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.pinPost(creatorId, id));
    }

    @DeleteMapping("/{id}/pin")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ContentPostResponse> unpinPost(@PathVariable UUID id) {
        UUID creatorId = getCurrentUserId();
        return ResponseEntity.ok(contentPostService.unpinPost(creatorId, id));
    }

    @Operation(summary = "Rechercher des utilisateurs à identifier dans une publication")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultats de recherche"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/users/search")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PagedResponse<MinimalUserDto>> searchUsersForTagging(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID creatorId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                PagedResponse.fromPage(contentPostService.searchUsersForTagging(creatorId, q, pageable)));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
