package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.SavedCollectionDtos.CollectionResponse;
import com.plateforme.marketplace.dto.SavedCollectionDtos.NameRequest;
import com.plateforme.marketplace.dto.SavedCollectionDtos.OverviewResponse;
import com.plateforme.marketplace.service.SavedCollectionService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Collections of saved posts — always the signed-in user's own (the route is authenticated). */
@RestController
@RequestMapping("/api/marketplace/collections")
@RequiredArgsConstructor
@Tag(name = "Saved collections", description = "Organise saved posts into named collections")
public class SavedCollectionController {

    private final SavedCollectionService collectionService;

    @Operation(summary = "All saved summary + every collection of the current user")
    @GetMapping
    public ResponseEntity<OverviewResponse> overview() {
        return ResponseEntity.ok(collectionService.getOverview(currentUserId()));
    }

    @Operation(summary = "Create a collection, optionally filing a post in it")
    @PostMapping
    public ResponseEntity<CollectionResponse> create(@Valid @RequestBody NameRequest request) {
        return ResponseEntity.ok(collectionService.create(currentUserId(), request.name(), request.postId()));
    }

    @Operation(summary = "Rename a collection")
    @PatchMapping("/{collectionId}")
    public ResponseEntity<CollectionResponse> rename(
            @PathVariable UUID collectionId, @Valid @RequestBody NameRequest request) {
        return ResponseEntity.ok(collectionService.rename(currentUserId(), collectionId, request.name()));
    }

    @Operation(summary = "Delete a collection (its posts stay saved)")
    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID collectionId) {
        collectionService.delete(currentUserId(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Posts of a collection, most recently filed first")
    @GetMapping("/{collectionId}/posts")
    public ResponseEntity<PagedResponse<ContentPostResponse>> posts(
            @PathVariable UUID collectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(PagedResponse.fromPage(
                collectionService.getPosts(currentUserId(), collectionId, pageable)));
    }

    @Operation(summary = "File a post in a collection (saves it if needed)")
    @PutMapping("/{collectionId}/posts/{postId}")
    public ResponseEntity<Void> addPost(@PathVariable UUID collectionId, @PathVariable UUID postId) {
        collectionService.addPost(currentUserId(), collectionId, postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Take a post out of a collection (it stays saved)")
    @DeleteMapping("/{collectionId}/posts/{postId}")
    public ResponseEntity<Void> removePost(@PathVariable UUID collectionId, @PathVariable UUID postId) {
        collectionService.removePost(currentUserId(), collectionId, postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ids of the current user's collections holding a post")
    @GetMapping("/containing/{postId}")
    public ResponseEntity<List<UUID>> containing(@PathVariable UUID postId) {
        return ResponseEntity.ok(collectionService.getCollectionIdsForPost(currentUserId(), postId));
    }

    private UUID currentUserId() {
        return ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }
}
