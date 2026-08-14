package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ProductGroupRequest;
import com.plateforme.marketplace.dto.ProductGroupResponse;
import com.plateforme.marketplace.service.MarketplaceProductGroupService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/creator/product-groups")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Creator Product Groups", description = "Named organizational groups for creator products")
@SecurityRequirement(name = "bearerAuth")
public class MarketplaceProductGroupController {

    private final MarketplaceProductGroupService groupService;

    @Operation(summary = "Create product group")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Group created"),
            @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PostMapping
    public ResponseEntity<ProductGroupResponse> createGroup(@Valid @RequestBody ProductGroupRequest request) {
        ProductGroupResponse body = groupService.createGroup(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List my product groups")
    @GetMapping
    public ResponseEntity<PagedResponse<ProductGroupResponse>> getMyGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(PagedResponse.fromPage(
                groupService.getMyGroups(getCurrentUserId(), pageable)));
    }

    @Operation(summary = "Get my product group")
    @GetMapping("/{id}")
    public ResponseEntity<ProductGroupResponse> getMyGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(groupService.getMyGroup(getCurrentUserId(), id));
    }

    @Operation(summary = "Update product group")
    @PutMapping("/{id}")
    public ResponseEntity<ProductGroupResponse> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody ProductGroupRequest request) {
        return ResponseEntity.ok(groupService.updateGroup(getCurrentUserId(), id, request));
    }

    @Operation(summary = "Delete product group (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        groupService.deleteGroup(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
