package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ContentPostBucket;
import com.plateforme.marketplace.dto.ContentPostRequest;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.MinimalUserDto;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.entity.ContentPostHide;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReactionType;
import com.plateforme.marketplace.repository.ContentCommentRepository;
import com.plateforme.marketplace.repository.ContentFavoriteRepository;
import com.plateforme.marketplace.repository.ContentPostHideRepository;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.repository.ContentReactionRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorSearchExpand;
import com.plateforme.user.service.FollowerPublishNotifyService;
import com.plateforme.user.service.SpecialtyTaxonomy;
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
    private static final int MAX_GALLERY_IMAGES = 10;
    private static final int MAX_REPOST_COMMENT = 3000;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("FILE", "GIF");

    private final ContentPostRepository contentPostRepository;
    private final ContentCommentRepository contentCommentRepository;
    private final ContentReactionRepository contentReactionRepository;
    private final ContentFavoriteRepository contentFavoriteRepository;
    private final ContentPostHideRepository contentPostHideRepository;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final FollowerPublishNotifyService followerPublishNotifyService;

    @Transactional
    public ContentPostResponse createPost(UUID creatorId, ContentPostRequest req) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "User not found."));

        validateRequest(req, creatorId);
        List<UUID> taggedIds = normalizeTaggedUserIds(req.taggedUserIds(), creatorId);

        ContentPost post = new ContentPost();
        post.setCreator(creator);
        applyRequest(post, req, taggedIds);

        post = contentPostRepository.save(post);

        if (Boolean.TRUE.equals(post.getIsPublic())) {
            followerPublishNotifyService.notifyFollowersNewContent(
                    creatorId, post.getId(), contentPreview(post));
        }

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
        CreatorProfile profile = creatorProfileRepository.findByUserId(creatorId).orElse(null);
        return page.map(p -> buildResponse(p, portfolioCount, null, null, profile, singleExtras(p)));
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getMyPosts(UUID creatorId, Pageable pageable) {
        return getMyPosts(creatorId, ContentPostBucket.ACTIVE, pageable);
    }

    @Transactional
    public void deletePost(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));

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
                    "This content is not in the trash.");
        }
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));
        assertOwner(post, creatorId);
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Contenu restauré depuis corbeille id={} par créateur={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public void permanentDeletePost(UUID creatorId, UUID postId) {
        if (contentPostRepository.findTrashById(creatorId, postId).isEmpty()) {
            throw new BusinessException("CONTENT_POST_NOT_FOUND",
                    "This content is not in the trash.");
        }
        int deleted = contentPostRepository.permanentDelete(creatorId, postId);
        if (deleted == 0) {
            throw new BusinessException("CONTENT_POST_NOT_FOUND",
                    "This content is not in the trash.");
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
                        "Content not found."));
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
        boolean wasPublic = Boolean.TRUE.equals(post.getIsPublic());
        post.setIsPublic(isPublic);
        post = contentPostRepository.save(post);
        if (isPublic && !wasPublic) {
            followerPublishNotifyService.notifyFollowersNewContent(
                    creatorId, post.getId(), contentPreview(post));
        }
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
                        "Content not found."));

        UUID ownerId = post.getCreator() != null ? post.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("This content does not belong to the current user");
        }

        validateRequest(req, creatorId);
        List<UUID> taggedIds = normalizeTaggedUserIds(req.taggedUserIds(), creatorId);
        boolean wasPublic = Boolean.TRUE.equals(post.getIsPublic());
        applyRequest(post, req, taggedIds);

        post = contentPostRepository.save(post);
        if (Boolean.TRUE.equals(post.getIsPublic()) && !wasPublic) {
            followerPublishNotifyService.notifyFollowersNewContent(
                    creatorId, post.getId(), contentPreview(post));
        }
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        log.info("Content updated id={} by creator={}", postId, creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional(readOnly = true)
    public ContentPostResponse getPublicPostById(UUID postId) {
        ContentPost post = contentPostRepository.findPublicById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));
        UUID creatorId = post.getCreator().getId();
        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional(readOnly = true)
    public ContentPostResponse getMyPostById(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));

        assertOwner(post, creatorId);

        long portfolioCount = contentPostRepository.countActiveByCreator_Id(creatorId);
        return toResponse(post, portfolioCount);
    }

    @Transactional
    public void incrementView(UUID postId) {
        ContentPost post = contentPostRepository.findPublicById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));
        int views = post.getViews() != null ? post.getViews() : 0;
        post.setViews(views + 1);
        contentPostRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getPublicPosts(
            UUID creatorId, String genre, String keyword, Pageable pageable, UUID viewerId) {
        String g = genre != null && !genre.isBlank() ? genre.trim() : null;
        String q = SpecialtyTaxonomy.sanitizeLabel(keyword);
        String qCanonical = "";
        String terms = "";
        if (q != null) {
            String canonical = SpecialtyTaxonomy.canonicalize(q);
            if (canonical != null && !canonical.equalsIgnoreCase(q)) {
                qCanonical = canonical;
            }
            terms = CreatorSearchExpand.expandedTermsPipe(q);
            if (terms.isEmpty()) {
                terms = q;
            }
        }

        Page<ContentPost> page = contentPostRepository.findPublicFiltered(
                creatorId, viewerId, g, q != null ? q : "", qCanonical, terms, pageable);
        return enrichPage(page, viewerId);
    }

    /** Public posts the viewer saved, most recently saved first. */
    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getSavedPosts(UUID viewerId, Pageable pageable) {
        return enrichPage(contentPostRepository.findSavedByUserId(viewerId, pageable), viewerId);
    }

    /**
     * Everything the cards need, resolved once for the whole page. Done per post this was a COUNT per
     * card for the portfolio total, plus — because the counts were missing from the payload entirely —
     * two HTTP round trips per card from the browser for the comment count and the viewer's own reaction.
     */
    private Page<ContentPostResponse> enrichPage(Page<ContentPost> page, UUID viewerId) {
        if (page.isEmpty()) {
            return page.map(p -> toResponse(p, 0L));
        }

        List<ContentPost> posts = page.getContent();
        List<UUID> postIds = posts.stream().map(ContentPost::getId).toList();

        /* Originals of the reposts on this page; a trashed original is simply absent here. */
        Set<UUID> originalIds = posts.stream()
                .map(ContentPost::getRepostOfId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ContentPost> originals = originalIds.isEmpty()
                ? Map.of()
                : contentPostRepository.findAllById(originalIds).stream()
                        .collect(Collectors.toMap(ContentPost::getId, o -> o, (a, b) -> a));

        Set<UUID> creatorIds = new java.util.HashSet<>();
        posts.forEach(post -> creatorIds.add(post.getCreator().getId()));
        originals.values().forEach(original -> creatorIds.add(original.getCreator().getId()));

        Map<UUID, Long> portfolioCounts = new LinkedHashMap<>();
        for (UUID cid : creatorIds) {
            portfolioCounts.put(cid, contentPostRepository.countActiveByCreator_Id(cid));
        }

        Map<UUID, Long> commentCounts = new LinkedHashMap<>();
        for (Object[] row : contentCommentRepository.countVisibleByTargetIds(ContentTargetType.POST, postIds)) {
            commentCounts.put((UUID) row[0], (Long) row[1]);
        }

        Set<UUID> likedByViewer = viewerId == null
                ? Set.of()
                : Set.copyOf(contentReactionRepository.findTargetIdsAmong(
                        viewerId, ContentTargetType.POST, ReactionType.LIKE, postIds));

        Set<UUID> savedByViewer = viewerId == null
                ? Set.of()
                : Set.copyOf(contentFavoriteRepository.findTargetIdsAmong(
                        viewerId, ContentTargetType.POST, postIds));

        /* A repost card's repost button acts on the original, so counts are keyed by that id. */
        Set<UUID> repostTargets = posts.stream().map(ContentPostService::repostTarget).collect(Collectors.toSet());
        Map<UUID, Long> repostCounts = new LinkedHashMap<>();
        for (Object[] row : contentPostRepository.countRepostsByOriginalIds(repostTargets)) {
            repostCounts.put((UUID) row[0], (Long) row[1]);
        }
        Set<UUID> repostedByViewer = viewerId == null
                ? Set.of()
                : Set.copyOf(contentPostRepository.findRepostedOriginalIds(viewerId, repostTargets));

        Map<UUID, CreatorProfile> profiles = creatorProfileRepository.findByUser_IdIn(creatorIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p, (a, b) -> a));

        return page.map(post -> {
            UUID target = repostTarget(post);
            ContentPost original = post.getRepostOfId() != null ? originals.get(post.getRepostOfId()) : null;
            ContentPostResponse embedded = original == null
                    ? null
                    : buildResponse(original, 0L, null, null,
                            profiles.get(original.getCreator().getId()), PostExtras.NONE);
            return buildResponse(
                    post,
                    portfolioCounts.getOrDefault(post.getCreator().getId(), 0L),
                    commentCounts.getOrDefault(post.getId(), 0L),
                    likedByViewer.contains(post.getId()) ? ReactionType.LIKE.name() : null,
                    profiles.get(post.getCreator().getId()),
                    new PostExtras(
                            embedded,
                            repostCounts.getOrDefault(target, 0L),
                            viewerId == null ? null : repostedByViewer.contains(target),
                            viewerId == null ? null : savedByViewer.contains(post.getId())));
        });
    }

    /** The post a repost button on this card acts on: the original for a repost, the post itself otherwise. */
    private static UUID repostTarget(ContentPost post) {
        return post.getRepostOfId() != null ? post.getRepostOfId() : post.getId();
    }

    /** Repost / save state that rides along a response; {@link #NONE} when a route does not compute it. */
    private record PostExtras(
            ContentPostResponse repostOf, long repostCount, Boolean viewerReposted, Boolean viewerSaved) {
        static final PostExtras NONE = new PostExtras(null, 0L, null, null);
    }

    @Transactional
    public ContentPostResponse repostPost(UUID creatorId, UUID postId, String comment) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));

        ContentPost original = requirePublicPost(postId);
        /* Reposting a repost reposts what it points at — chains never nest. */
        if (original.getRepostOfId() != null) {
            original = requirePublicPost(original.getRepostOfId());
        }
        if (Objects.equals(original.getCreator().getId(), creatorId)) {
            throw new BusinessException("REPOST_OWN_POST", "You cannot repost your own post.");
        }
        if (contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, original.getId()).isPresent()) {
            throw new BusinessException("ALREADY_REPOSTED", "You already reposted this post.");
        }
        String note = blankToNull(comment);
        if (note != null && note.length() > MAX_REPOST_COMMENT) {
            throw new BusinessException("REPOST_COMMENT_TOO_LONG",
                    "Your comment is too long (max " + MAX_REPOST_COMMENT + " characters).");
        }

        ContentPost repost = new ContentPost();
        repost.setCreator(creator);
        repost.setRepostOfId(original.getId());
        repost.setTitle(note);
        repost.setIsPublic(true);
        repost.setCommentsEnabled(true);
        repost = contentPostRepository.save(repost);

        log.info("Repost created id={} original={} by creator={}", repost.getId(), original.getId(), creatorId);
        return toResponse(repost, contentPostRepository.countActiveByCreator_Id(creatorId));
    }

    /** Undoes the creator's repost of {@code postId} (the original, or one of its reposts). */
    @Transactional
    public void undoRepost(UUID creatorId, UUID postId) {
        ContentPost target = contentPostRepository.findById(postId).orElse(null);
        UUID originalId = target != null && target.getRepostOfId() != null ? target.getRepostOfId() : postId;
        ContentPost repost = contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, originalId)
                .orElseThrow(() -> new BusinessException("REPOST_NOT_FOUND", "You have not reposted this post."));
        repost.setDeletedAt(LocalDateTime.now());
        repost.setPinnedAt(null);
        contentPostRepository.save(repost);
        log.info("Repost removed id={} original={} by creator={}", repost.getId(), originalId, creatorId);
    }

    @Transactional
    public void hidePost(UUID userId, UUID postId) {
        ContentPost post = requirePublicPost(postId);
        if (Objects.equals(post.getCreator().getId(), userId)) {
            throw new BusinessException("HIDE_OWN_POST", "You cannot hide your own post.");
        }
        if (!contentPostHideRepository.existsByUserIdAndPostId(userId, postId)) {
            contentPostHideRepository.save(new ContentPostHide(userId, postId));
        }
    }

    @Transactional
    public void unhidePost(UUID userId, UUID postId) {
        contentPostHideRepository.deleteByUserIdAndPostId(userId, postId);
    }

    private ContentPost requirePublicPost(UUID postId) {
        return contentPostRepository.findPublicById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND", "Content not found."));
    }

    @Transactional(readOnly = true)
    public Page<MinimalUserDto> searchUsersForTagging(UUID creatorId, String query, Pageable pageable) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 2) {
            return Page.empty(pageable);
        }
        return userRepository.searchByFullNameExcluding(q, creatorId, pageable)
                .map(u -> new MinimalUserDto(u.getId(), u.getFullName(), u.getPublicUsername(), u.getAvatarUrl()));
    }

    public ContentPostResponse toResponse(ContentPost post, long portfolioCount) {
        /* null counts tell the client to fetch them itself rather than render a wrong zero. */
        return toResponse(post, portfolioCount, null, null);
    }

    public ContentPostResponse toResponse(
            ContentPost post, long portfolioCount, Long commentCount, String viewerReaction) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(post.getCreator().getId()).orElse(null);
        return buildResponse(post, portfolioCount, commentCount, viewerReaction, profile, singleExtras(post));
    }

    /**
     * Repost state for routes that build one response at a time (they do not know the viewer, so the
     * viewer flags stay {@code null} and the client asks for them itself).
     */
    private PostExtras singleExtras(ContentPost post) {
        ContentPostResponse embedded = null;
        if (post.getRepostOfId() != null) {
            ContentPost original = contentPostRepository.findById(post.getRepostOfId()).orElse(null);
            if (original != null) {
                CreatorProfile originalProfile =
                        creatorProfileRepository.findByUserId(original.getCreator().getId()).orElse(null);
                embedded = buildResponse(original, 0L, null, null, originalProfile, PostExtras.NONE);
            }
        }
        return new PostExtras(embedded, contentPostRepository.countByRepostOfId(repostTarget(post)), null, null);
    }

    /** {@code profile} is the creator's profile, resolved by the caller so lists load it once. */
    private ContentPostResponse buildResponse(
            ContentPost post, long portfolioCount, Long commentCount, String viewerReaction,
            CreatorProfile profile, PostExtras extras) {
        User creator = post.getCreator();
        String appRole = profile != null ? profile.getAppRole() : null;
        String specialite = profile != null ? profile.getSpecialite() : null;
        List<String> specialties = profile != null && profile.getSpecialties() != null
                ? profile.getSpecialties()
                : List.of();
        MinimalUserDto minimal = new MinimalUserDto(
                creator.getId(),
                creator.getFullName(),
                creator.getPublicUsername(),
                creator.getAvatarUrl(),
                appRole,
                specialite,
                specialties
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
                minimal,
                commentCount,
                viewerReaction,
                galleryOf(post),
                extras.repostOf(),
                extras.repostCount(),
                extras.viewerReposted(),
                extras.viewerSaved()
        );
    }

    /** The full ordered gallery: the stored one for multi-image posts, else just the cover. */
    private static List<String> galleryOf(ContentPost post) {
        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            return List.copyOf(post.getMediaUrls());
        }
        return post.getMediaUrl() != null && !post.getMediaUrl().isBlank() ? List.of(post.getMediaUrl()) : List.of();
    }

    private ContentPost requireActivePost(UUID creatorId, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("CONTENT_POST_NOT_FOUND",
                        "Content not found."));
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
        boolean hasMedia = (req.mediaUrl() != null && !req.mediaUrl().isBlank())
                || !normalizeMediaUrls(req.mediaUrls()).isEmpty();
        boolean hasText = (req.title() != null && !req.title().isBlank())
                || (req.description() != null && !req.description().isBlank());
        if (!hasMedia && !hasText) {
            throw new BusinessException("CONTENT_EMPTY", "Write something or add a media file.");
        }

        if (countStringListItems(req.toolsUsed()) > MAX_LIST_ITEMS) {
            throw new BusinessException("TOOLS_USED_LIMIT", "A maximum of 10 tools is allowed.");
        }

        if (countStringListItems(req.tags()) > MAX_LIST_ITEMS) {
            throw new BusinessException("TAGS_LIMIT", "Maximum 10 tags allowed");
        }

        String mediaType = normalizeMediaType(req.mediaType());
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new BusinessException("INVALID_MEDIA_TYPE", "Unsupported media type.");
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
        List<String> gallery = normalizeMediaUrls(req.mediaUrls());
        if (!gallery.isEmpty()) {
            /* The cover is always the first image, whatever `mediaUrl` says. */
            mediaUrl = gallery.get(0);
            if (gallery.size() > 1) {
                mediaType = "FILE";
            }
        }
        if (mediaUrl == null) {
            mediaType = "FILE";
        } else if ("GIF".equals(mediaType)) {
            mediaType = "GIF";
        } else if (!"GIF".equals(mediaType)) {
            mediaType = "FILE";
        }

        post.setTitle(blankToNull(req.title()));
        post.setGenre(blankToNull(req.genre()));
        if (req.mediaUrls() != null) {
            post.setMediaUrls(gallery.size() > 1 ? new ArrayList<>(gallery) : new ArrayList<>());
        } else if (!Objects.equals(mediaUrl, post.getMediaUrl())) {
            /* A client that predates galleries swapped the cover: the old gallery no longer applies. */
            post.setMediaUrls(new ArrayList<>());
        }
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
                result.add(new MinimalUserDto(user.getId(), user.getFullName(), user.getPublicUsername(), user.getAvatarUrl()));
            }
        }
        return result;
    }

    /** Trimmed, de-duplicated, order-preserving gallery; rejects more than the cap or a non-URL entry. */
    private static List<String> normalizeMediaUrls(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .distinct()
                .toList();
        if (cleaned.size() > MAX_GALLERY_IMAGES) {
            throw new BusinessException("MEDIA_URLS_LIMIT",
                    "A post can hold at most " + MAX_GALLERY_IMAGES + " images.");
        }
        for (String url : cleaned) {
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("/")) {
                throw new BusinessException("INVALID_MEDIA_URL", "One of the images has an invalid address.");
            }
        }
        return cleaned;
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return "FILE";
        }
        return mediaType.trim().toUpperCase();
    }

    private static String contentPreview(ContentPost post) {
        if (post.getTitle() != null && !post.getTitle().isBlank()) {
            return post.getTitle().trim();
        }
        if (post.getDescription() != null && !post.getDescription().isBlank()) {
            return post.getDescription().trim();
        }
        return "a new post";
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
