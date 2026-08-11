package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ContentPostBucket;
import com.plateforme.marketplace.dto.ContentPostRequest;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.MinimalUserDto;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentPostService {

    private static final int MAX_TAGGED_USERS = 5;
    private static final int MAX_LIST_ITEMS = 10;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("FILE", "GIF");

    private final ContentPostRepository contentPostRepository;
    private final UserRepository userRepository;

    @Transactional
    public ContentPostResponse createPost(UUID creatorId, ContentPostRequest req) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + creatorId));

        validateRequest(req, creatorId);
        List<UUID> taggedIds = normalizeTaggedUserIds(req.taggedUserIds(), creatorId);

        ContentPost post = new ContentPost();
        post.setCreator(creator);
        applyRequest(post, req, taggedIds);

        post = contentPostRepository.save(post);

        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getMyPosts(UUID creatorId, ContentPostBucket bucket, Pageable pageable) {
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        Page<ContentPost> page = switch (bucket) {
            case PINNED -> contentPostRepository.findPinnedByCreatorId(creatorId, pageable);
            case ARCHIVED -> contentPostRepository.findArchivedByCreatorId(creatorId, pageable);
            case TRASH -> contentPostRepository.findTrashByCreatorId(creatorId, pageable);
            case ACTIVE -> contentPostRepository.findActiveByCreatorId(creatorId, pageable);
        };
        return page.map(p -> toResponse(p, portfolioCount));
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getMyPosts(UUID creatorId, Pageable pageable) {
        return getMyPosts(creatorId, ContentPostBucket.ACTIVE, pageable);
    }

    @Transactional
    public void deletePost(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Contenu introuvable : " + postId));

        UUID ownerId = post.getCreator() != null ? post.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("Ce contenu n'appartient pas à l'utilisateur courant");
        }

        post.setDeletedAt(LocalDateTime.now());
        post.setArchivedAt(null);
        post.setPinnedAt(null);
        contentPostRepository.save(post);
        log.info("Contenu déplacé vers corbeille id={} par créateur={}", postId, creatorId);
    }

    @Transactional
    public ContentPostResponse restorePost(UUID creatorId, UUID postId) {
        int updated = contentPostRepository.restoreFromTrash(creatorId, postId);
        if (updated == 0) {
            throw new BusinessException("CONTENT_POST_NOT_FOUND",
                    "Contenu introuvable dans la corbeille : " + postId);
        }
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Contenu introuvable : " + postId));
        assertOwner(post, creatorId);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Contenu restauré depuis corbeille id={} par créateur={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public void permanentDeletePost(UUID creatorId, UUID postId) {
        if (contentPostRepository.findTrashById(creatorId, postId).isEmpty()) {
            throw new BusinessException("CONTENT_POST_NOT_FOUND",
                    "Contenu introuvable dans la corbeille : " + postId);
        }
        int deleted = contentPostRepository.permanentDelete(creatorId, postId);
        if (deleted == 0) {
            throw new BusinessException("CONTENT_POST_NOT_FOUND",
                    "Contenu introuvable dans la corbeille : " + postId);
        }
        log.info("Contenu supprimé définitivement id={} par créateur={}", postId, creatorId);
    }

    @Transactional
    public ContentPostResponse archivePost(UUID creatorId, UUID postId) {
        ContentPost post = requireActivePost(creatorId, postId);
        post.setArchivedAt(LocalDateTime.now());
        post.setPinnedAt(null);
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Contenu archivé id={} par créateur={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse unarchivePost(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Contenu introuvable : " + postId));
        assertOwner(post, creatorId);
        if (post.getArchivedAt() == null) {
            throw new BusinessException("CONTENT_NOT_ARCHIVED", "This content is not archived");
        }
        post.setArchivedAt(null);
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Contenu désarchivé id={} par créateur={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse pinPost(UUID creatorId, UUID postId) {
        ContentPost post = requireActivePost(creatorId, postId);
        post.setPinnedAt(LocalDateTime.now());
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse unpinPost(UUID creatorId, UUID postId) {
        ContentPost post = requireActivePost(creatorId, postId);
        post.setPinnedAt(null);
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse updateVisibility(UUID creatorId, UUID postId, boolean isPublic) {
        ContentPost post = requireActivePost(creatorId, postId);
        post.setIsPublic(isPublic);
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse updateCommentsEnabled(UUID creatorId, UUID postId, boolean commentsEnabled) {
        ContentPost post = requireActivePost(creatorId, postId);
        post.setCommentsEnabled(commentsEnabled);
        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Commentaires {} pour contenu id={} par créateur={}",
                commentsEnabled ? "activés" : "désactivés", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public ContentPostResponse updatePost(UUID creatorId, UUID postId, ContentPostRequest req) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found: " + postId));

        UUID ownerId = post.getCreator() != null ? post.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("This content does not belong to the current user");
        }

        validateRequest(req, creatorId);
        List<UUID> taggedIds = normalizeTaggedUserIds(req.taggedUserIds(), creatorId);
        applyRequest(post, req, taggedIds);

        post = contentPostRepository.save(post);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Content updated id={} by creator={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional(readOnly = true)
    public ContentPostResponse getPublicPostById(UUID postId) {
        ContentPost post = contentPostRepository.findPublicById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Public content not found: " + postId));
        UUID creatorId = post.getCreator().getId();
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional(readOnly = true)
    public ContentPostResponse getMyPostById(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found: " + postId));

        assertOwner(post, creatorId);

        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public void incrementView(UUID postId) {
        ContentPost post = contentPostRepository.findPublicById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Public content not found: " + postId));
        int views = post.getViews() != null ? post.getViews() : 0;
        post.setViews(views + 1);
        contentPostRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getPublicPosts(UUID creatorId, String genre, String keyword, Pageable pageable) {
        String q = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        String g = genre != null && !genre.isBlank() ? genre.trim() : null;

        return contentPostRepository.findPublicFiltered(creatorId, g, q, pageable)
                .map(p -> {
                    UUID cid = p.getCreator().getId();
                    long portfolioCount = contentPostRepository.countActiveByCreator_Id(cid);
                    return toResponse(p, portfolioCount);
                });
    }

    @Transactional(readOnly = true)
    public Page<MinimalUserDto> searchUsersForTagging(UUID creatorId, String query, Pageable pageable) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 2) {
            return Page.empty(pageable);
        }
        return userRepository.searchByFullNameExcluding(q, creatorId, pageable)
                .map(u -> new MinimalUserDto(u.getId(), u.getFullName(), u.getAvatarUrl()));
    }

    public ContentPostResponse toResponse(ContentPost post, long portfolioCount) {
        User creator = post.getCreator();
        MinimalUserDto minimal = new MinimalUserDto(
                creator.getId(),
                creator.getFullName(),
                creator.getAvatarUrl()
        );
        List<String> tools = post.getToolsUsed() != null ? post.getToolsUsed() : List.of();
        List<String> tags = post.getTags() != null ? post.getTags() : List.of();
        List<MinimalUserDto> taggedUsers = resolveTaggedUsers(post.getTaggedUserIds());

        return new ContentPostResponse(
                post.getId(),
                post.getTitle(),
                post.getGenre(),
                post.getMediaUrl(),
                post.getMediaType() != null ? post.getMediaType() : "FILE",
                post.getTextColor(),
                post.getMoodLabel(),
                post.getMoodEmoji(),
                taggedUsers,
                post.getDescription(),
                post.getPriceInfo(),
                tools,
                tags,
                Boolean.TRUE.equals(post.getIsPublic()),
                Boolean.TRUE.equals(post.getCommentsEnabled()),
                post.getPinnedAt() != null,
                post.getArchivedAt(),
                post.getViews() != null ? post.getViews() : 0,
                post.getLikes() != null ? post.getLikes() : 0,
                portfolioCount,
                post.getCreatedAt(),
                minimal
        );
    }

    private ContentPost requireActivePost(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Contenu introuvable : " + postId));
        assertOwner(post, creatorId);
        if (post.getArchivedAt() != null) {
            throw new BusinessException("CONTENT_ARCHIVED", "Archived content cannot be modified this way");
        }
        return post;
    }

    private void assertOwner(ContentPost post, UUID creatorId) {
        UUID ownerId = post.getCreator() != null ? post.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("Ce contenu n'appartient pas à l'utilisateur courant");
        }
    }

    private void validateRequest(ContentPostRequest req, UUID creatorId) {
        if (req.mediaUrl() == null || req.mediaUrl().isBlank()) {
            throw new BusinessException("MEDIA_REQUIRED", "Media file is required");
        }

        if (countStringListItems(req.toolsUsed()) > MAX_LIST_ITEMS) {
            throw new BusinessException("TOOLS_USED_LIMIT", "Maximum 10 outils autorisés");
        }

        if (countStringListItems(req.tags()) > MAX_LIST_ITEMS) {
            throw new BusinessException("TAGS_LIMIT", "Maximum 10 tags allowed");
        }

        String mediaType = normalizeMediaType(req.mediaType());
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new BusinessException("INVALID_MEDIA_TYPE", "Unsupported media type: " + mediaType);
        }

        List<UUID> tagged = req.taggedUserIds() != null ? req.taggedUserIds() : List.of();
        if (tagged.size() > MAX_TAGGED_USERS) {
            throw new BusinessException("TAGGED_USERS_LIMIT", "Maximum " + MAX_TAGGED_USERS + " tagged users");
        }
        if (tagged.stream().anyMatch(id -> Objects.equals(id, creatorId))) {
            throw new BusinessException("INVALID_TAGGED_USER", "You cannot tag yourself");
        }
    }

    private List<UUID> normalizeTaggedUserIds(List<UUID> raw, UUID creatorId) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<UUID> unique = raw.stream()
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, creatorId))
                .distinct()
                .limit(MAX_TAGGED_USERS)
                .toList();
        if (unique.isEmpty()) {
            return List.of();
        }
        List<User> found = userRepository.findByIdInAndDeletedAtIsNull(unique);
        if (found.size() != unique.size()) {
            throw new BusinessException("INVALID_TAGGED_USER", "One or more tagged users were not found");
        }
        return new ArrayList<>(unique);
    }

    private void applyRequest(ContentPost post, ContentPostRequest req, List<UUID> taggedIds) {
        List<String> tools = normalizeStringList(req.toolsUsed());
        List<String> tags = normalizeStringList(req.tags());
        String mediaUrl = req.mediaUrl() != null && !req.mediaUrl().isBlank() ? req.mediaUrl().trim() : null;
        String mediaType = normalizeMediaType(req.mediaType());
        if (mediaUrl == null) {
            mediaType = "FILE";
        } else if ("GIF".equals(mediaType)) {
            mediaType = "GIF";
        } else if (!"GIF".equals(mediaType)) {
            mediaType = "FILE";
        }

        post.setTitle(blankToNull(req.title()));
        post.setGenre(blankToNull(req.genre()));
        post.setMediaUrl(mediaUrl);
        post.setMediaType(mediaType);
        post.setTextColor(blankToNull(req.textColor()));
        post.setMoodLabel(blankToNull(req.moodLabel()));
        post.setMoodEmoji(blankToNull(req.moodEmoji()));
        post.setTaggedUserIds(new ArrayList<>(taggedIds));
        post.setDescription(blankToNull(req.description()));
        post.setPriceInfo(blankToNull(req.priceInfo()));
        post.setToolsUsed(new ArrayList<>(tools));
        post.setTags(new ArrayList<>(tags));
        post.setIsPublic(Boolean.TRUE.equals(req.isPublic()));
        if (req.commentsEnabled() != null) {
            post.setCommentsEnabled(Boolean.TRUE.equals(req.commentsEnabled()));
        } else if (post.getCommentsEnabled() == null) {
            post.setCommentsEnabled(true);
        }
    }

    private List<MinimalUserDto> resolveTaggedUsers(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<User> users = userRepository.findByIdInAndDeletedAtIsNull(ids);
        Map<UUID, User> byId = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, LinkedHashMap::new));
        List<MinimalUserDto> result = new ArrayList<>();
        for (UUID id : ids) {
            User user = byId.get(id);
            if (user != null) {
                result.add(new MinimalUserDto(user.getId(), user.getFullName(), user.getAvatarUrl()));
            }
        }
        return result;
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return "FILE";
        }
        return mediaType.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static List<String> normalizeStringList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static int countStringListItems(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        return (int) raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();
    }
}
