package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.CreatorProfileResponse;
import com.plateforme.marketplace.dto.MarketplaceProductResponse;
import com.plateforme.marketplace.dto.ProductGroupResponse;
import com.plateforme.marketplace.dto.ProductPreviewResponse;
import com.plateforme.marketplace.service.MarketplaceProductGroupService;
import com.plateforme.marketplace.entity.ProductType;
import com.plateforme.marketplace.dto.CreatorProfileViewResponse;
import com.plateforme.marketplace.dto.RecordCreatorProfileViewRequest;
import com.plateforme.marketplace.dto.SendCreatorContactMessageRequest;
import com.plateforme.marketplace.service.ContentPostService;
import com.plateforme.marketplace.service.CreatorContactMessageService;
import com.plateforme.marketplace.service.CreatorProfileViewService;
import com.plateforme.marketplace.service.MarketplaceAccessService;
import com.plateforme.marketplace.service.MarketplaceProductService;
import com.plateforme.marketplace.service.MarketplaceService;
import com.plateforme.shared.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.plateforme.user.dto.CreatorFollowStatsDto;
import com.plateforme.user.dto.CreatorReputationDto;
import com.plateforme.user.dto.CreatorReviewItemDto;
import com.plateforme.user.dto.SubmitCreatorReviewDto;
import com.plateforme.user.entity.User;
import com.plateforme.user.service.CreatorFollowService;
import com.plateforme.user.service.CreatorReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Marketplace", description = "Marketplace publique (GET sans auth)")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final ContentPostService contentPostService;
    private final MarketplaceProductService productService;
    private final MarketplaceAccessService accessService;
    private final MarketplaceProductGroupService productGroupService;
    private final CreatorReviewService creatorReviewService;
    private final CreatorFollowService creatorFollowService;
    private final CreatorProfileViewService creatorProfileViewService;
    private final CreatorContactMessageService creatorContactMessageService;

    @Operation(summary = "Lister les créateurs",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée")
    })
    @GetMapping("/creators")
    public ResponseEntity<PagedResponse<CreatorProfileResponse>> getCreators(
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) Integer minYearsExperience,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        UUID viewerUserId = resolveViewerUserId(authentication);
        return ResponseEntity.ok(PagedResponse.fromPage(
                marketplaceService.getCreators(
                        specialite, verified, available, nationality, minYearsExperience,
                        lat, lng, sort, viewerUserId, pageable)));
    }

    @Operation(summary = "Rechercher des créateurs",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultats paginés")
    })
    @GetMapping("/creators/search")
    public ResponseEntity<PagedResponse<CreatorProfileResponse>> searchCreators(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) Integer minYearsExperience,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        UUID viewerUserId = resolveViewerUserId(authentication);
        return ResponseEntity.ok(PagedResponse.fromPage(
                marketplaceService.searchCreators(
                        q, verified, available, nationality, specialite, minYearsExperience,
                        lat, lng, sort, viewerUserId, pageable)));
    }

    @Operation(summary = "Autocomplete specialties already used on profiles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching specialty labels")
    })
    @GetMapping("/creators/specialties")
    public ResponseEntity<List<String>> suggestSpecialties(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(marketplaceService.suggestSpecialties(q));
    }

    @Operation(summary = "Profil public créateur",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil créateur"),
            @ApiResponse(responseCode = "404", description = "Créateur introuvable")
    })
    @GetMapping("/creators/{id}")
    public ResponseEntity<CreatorProfileResponse> getCreatorPublicProfile(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID viewerUserId = resolveViewerUserId(authentication);
        return ResponseEntity.ok(marketplaceService.getCreatorPublicProfile(id, viewerUserId));
    }

    @Operation(summary = "List public product catalogues for a creator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue list"),
            @ApiResponse(responseCode = "404", description = "Creator not found")
    })
    @GetMapping("/creators/{id}/product-groups")
    public ResponseEntity<PagedResponse<ProductGroupResponse>> getCreatorProductGroups(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(PagedResponse.fromPage(productGroupService.getPublicGroups(id, pageable)));
    }

    @Operation(summary = "Record a visit on a creator public profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visit processed"),
            @ApiResponse(responseCode = "404", description = "Creator not found")
    })
    @PostMapping("/creators/{id}/view")
    public ResponseEntity<CreatorProfileViewResponse> recordCreatorProfileView(
            @PathVariable UUID id,
            @RequestBody(required = false) RecordCreatorProfileViewRequest body,
            Authentication authentication) {
        UUID viewerUserId = resolveViewerUserId(authentication);
        String visitorKey = body != null ? body.visitorKey() : null;
        return ResponseEntity.ok(creatorProfileViewService.recordView(id, viewerUserId, visitorKey));
    }

    @Operation(summary = "Send a contact message to a creator via their public portfolio")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Message accepted and emailed"),
            @ApiResponse(responseCode = "400", description = "Validation failed or creator/contact unavailable"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/creators/{id}/contact-messages")
    public ResponseEntity<Void> sendCreatorContactMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendCreatorContactMessageRequest body,
            HttpServletRequest request) {
        creatorContactMessageService.sendContactMessage(id, body, resolveClientIp(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Contenus publics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée")
    })
    @GetMapping("/contents")
    public ResponseEntity<PagedResponse<ContentPostResponse>> getPublicContents(
            @RequestParam(required = false) UUID creatorId,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                contentPostService.getPublicPosts(creatorId, genre, q, pageable)));
    }

    @Operation(summary = "Détail d'un contenu public")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenu trouvé"),
            @ApiResponse(responseCode = "404", description = "Contenu introuvable")
    })
    @GetMapping("/contents/{id}")
    public ResponseEntity<ContentPostResponse> getPublicContent(@PathVariable UUID id) {
        return ResponseEntity.ok(contentPostService.getPublicPostById(id));
    }

    @Operation(summary = "Incrémenter les vues d'un contenu public")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vue comptabilisée"),
            @ApiResponse(responseCode = "404", description = "Contenu introuvable")
    })
    @PostMapping("/contents/{id}/view")
    public ResponseEntity<Void> incrementContentView(@PathVariable UUID id) {
        contentPostService.incrementView(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List published marketplace products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated product list")
    })
    @GetMapping("/products")
    public ResponseEntity<PagedResponse<MarketplaceProductResponse>> getPublishedProducts(
            @RequestParam(required = false) UUID creatorId,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minPriceCents,
            @RequestParam(required = false) Integer maxPriceCents,
            @RequestParam(required = false) String format,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "false") boolean favoritesOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        int safeSize = Math.min(Math.max(size, 1), 1000);
        Pageable pageable = PageRequest.of(page, safeSize, resolveProductSort(sort));
        UUID favoritesUserId = null;
        if (favoritesOnly) {
            if (!isAuthenticatedUser(authentication)) {
                return ResponseEntity.ok(PagedResponse.fromPage(Page.empty(pageable)));
            }
            favoritesUserId = ((User) authentication.getPrincipal()).getId();
        }
        return ResponseEntity.ok(PagedResponse.fromPage(
                productService.getPublishedProducts(
                        creatorId, type, genre, q, minPriceCents, maxPriceCents, favoritesUserId, format, pageable)));
    }

    private Sort resolveProductSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceCents");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceCents");
            case "popular" -> Sort.by(Sort.Direction.DESC, "likes");
            case "views" -> Sort.by(Sort.Direction.DESC, "views");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    @GetMapping("/products/{id}/similar")
    public ResponseEntity<List<MarketplaceProductResponse>> getSimilarProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "6") int size) {
        int safeSize = Math.min(Math.max(size, 1), 20);
        return ResponseEntity.ok(productService.getSimilarProducts(id, safeSize));
    }

    @Operation(summary = "Get published marketplace product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/products/{id}")
    public ResponseEntity<MarketplaceProductResponse> getPublishedProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getPublishedProduct(id));
    }

    @Operation(summary = "Get product preview (demo or limited preview)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preview metadata"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/products/{id}/preview")
    public ResponseEntity<ProductPreviewResponse> getProductPreview(@PathVariable UUID id) {
        return ResponseEntity.ok(accessService.getProductPreview(id));
    }

    @Operation(summary = "Creator follow stats")
    @GetMapping("/creators/{id}/follow-stats")
    public ResponseEntity<CreatorFollowStatsDto> getCreatorFollowStats(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(creatorFollowService.getStats(id, resolveViewerUserId(authentication)));
    }

    @Operation(summary = "Follow a creator", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/creators/{id}/follow")
    public ResponseEntity<CreatorFollowStatsDto> followCreator(
            @PathVariable UUID id,
            Authentication authentication) {
        if (!isAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).build();
        }
        UUID followerId = ((User) authentication.getPrincipal()).getId();
        creatorFollowService.follow(followerId, id);
        return ResponseEntity.ok(creatorFollowService.getStats(id, followerId));
    }

    @Operation(summary = "Unfollow a creator", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/creators/{id}/follow")
    public ResponseEntity<CreatorFollowStatsDto> unfollowCreator(
            @PathVariable UUID id,
            Authentication authentication) {
        if (!isAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).build();
        }
        UUID followerId = ((User) authentication.getPrincipal()).getId();
        creatorFollowService.unfollow(followerId, id);
        return ResponseEntity.ok(creatorFollowService.getStats(id, followerId));
    }

    @Operation(summary = "List creators I follow")
    @GetMapping("/following/creators")
    public ResponseEntity<PagedResponse<CreatorProfileResponse>> getFollowingCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (!isAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).build();
        }
        UUID followerId = ((User) authentication.getPrincipal()).getId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                marketplaceService.getFollowingCreators(followerId, pageable)));
    }

    @Operation(summary = "Creator reputation summary")
    @GetMapping("/creators/{id}/reputation")
    public ResponseEntity<CreatorReputationDto> getCreatorReputation(@PathVariable UUID id) {
        return ResponseEntity.ok(creatorReviewService.getReputation(id, 10));
    }

    @Operation(summary = "Submit a creator review")
    @PostMapping("/creators/{id}/reviews")
    public ResponseEntity<CreatorReviewItemDto> submitCreatorReview(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitCreatorReviewDto dto,
            Authentication authentication) {
        if (!isAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).build();
        }
        UUID reviewerId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(creatorReviewService.submitReview(id, reviewerId, dto));
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User;
    }

    private UUID resolveViewerUserId(Authentication authentication) {
        if (!isAuthenticatedUser(authentication)) {
            return null;
        }
        return ((User) authentication.getPrincipal()).getId();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
