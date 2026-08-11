package com.plateforme.user.service;

import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.service.ContentPostService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.UpdatePortfolioRequest;
import com.plateforme.user.entity.CreatorPortfolioPost;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorPortfolioPostRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorPortfolioService {

    static final int MAX_PORTFOLIO_ITEMS = 4;

    private final CreatorPortfolioPostRepository portfolioPostRepository;
    private final ContentPostRepository contentPostRepository;
    private final ContentPostService contentPostService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ContentPostResponse> getCuratedPostsForOwner(UUID creatorUserId) {
        requireCreatorUser(creatorUserId);
        long portfolioCount = portfolioPostRepository.countPublicCuratedByCreatorId(creatorUserId);
        return portfolioPostRepository.findActiveCuratedByCreatorId(creatorUserId).stream()
                .map(CreatorPortfolioPost::getContentPost)
                .map(post -> contentPostService.toResponse(post, portfolioCount))
                .toList();
    }

    @Transactional
    public List<ContentPostResponse> updateCuratedPosts(UUID creatorUserId, UpdatePortfolioRequest request) {
        User creator = requireCreatorUser(creatorUserId);
        List<UUID> requestedIds = request.contentPostIds();

        if (requestedIds.size() > MAX_PORTFOLIO_ITEMS) {
            throw new BusinessException("TOO_MANY_PORTFOLIO_ITEMS",
                    "A maximum of " + MAX_PORTFOLIO_ITEMS + " portfolio items is allowed.");
        }

        Set<UUID> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new BusinessException("DUPLICATE_PORTFOLIO_ITEMS", "Portfolio post IDs must be unique.");
        }

        Map<UUID, ContentPost> ownedPosts = Map.of();
        if (!requestedIds.isEmpty()) {
            List<ContentPost> posts = contentPostRepository.findAllById(requestedIds);
            ownedPosts = posts.stream()
                    .filter(post -> post.getCreator() != null && creatorUserId.equals(post.getCreator().getId()))
                    .filter(post -> post.getDeletedAt() == null && post.getArchivedAt() == null)
                    .collect(Collectors.toMap(ContentPost::getId, Function.identity()));

            if (ownedPosts.size() != requestedIds.size()) {
                throw new BusinessException("PORTFOLIO_POST_NOT_OWNED",
                        "All portfolio posts must belong to you and be active.");
            }
        }

        portfolioPostRepository.deleteAllByCreatorUserId(creatorUserId);
        List<CreatorPortfolioPost> entries = new ArrayList<>();
        for (int i = 0; i < requestedIds.size(); i++) {
            UUID postId = requestedIds.get(i);
            ContentPost post = ownedPosts.get(postId);
            entries.add(new CreatorPortfolioPost(creator, post, i));
        }
        portfolioPostRepository.saveAll(entries);
        log.info("Portfolio curation updated for creator={} with {} items", creatorUserId, entries.size());

        return getCuratedPostsForOwner(creatorUserId);
    }

    @Transactional(readOnly = true)
    public List<ContentPostResponse> getPublicCuratedPosts(UUID creatorUserId) {
        long portfolioCount = portfolioPostRepository.countPublicCuratedByCreatorId(creatorUserId);
        return portfolioPostRepository.findPublicCuratedByCreatorId(creatorUserId).stream()
                .map(CreatorPortfolioPost::getContentPost)
                .map(post -> contentPostService.toResponse(post, portfolioCount))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPublicCuratedPosts(UUID creatorUserId) {
        return portfolioPostRepository.countPublicCuratedByCreatorId(creatorUserId);
    }

    private User requireCreatorUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + userId));
        boolean hasCreatorRole = user.getRoles().stream()
                .anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!hasCreatorRole) {
            throw new BusinessException("ROLE_REQUIRED",
                    "L'utilisateur doit avoir le rôle CREATOR pour gérer un profil créateur");
        }
        return user;
    }
}
