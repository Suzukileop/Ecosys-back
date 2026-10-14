package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.SavedCollectionDtos.CollectionResponse;
import com.plateforme.marketplace.dto.SavedCollectionDtos.Cover;
import com.plateforme.marketplace.dto.SavedCollectionDtos.OverviewResponse;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.SavedCollection;
import com.plateforme.marketplace.entity.SavedCollectionItem;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.repository.SavedCollectionItemRepository;
import com.plateforme.marketplace.repository.SavedCollectionRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Collections of saved posts. A collection only ever holds posts its owner has saved: filing a
 * post saves it, and un-saving a post takes it out of every collection (MarketplaceSocialService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedCollectionService {

    static final int MAX_COLLECTIONS = 100;
    static final int MAX_NAME_LENGTH = 60;
    private static final int COVER_TILES = 4;

    private final SavedCollectionRepository collectionRepository;
    private final SavedCollectionItemRepository itemRepository;
    private final ContentPostRepository contentPostRepository;
    private final UserRepository userRepository;
    private final MarketplaceSocialService socialService;
    private final ContentPostService contentPostService;

    @Transactional(readOnly = true)
    public OverviewResponse getOverview(UUID userId) {
        Page<ContentPost> recentSaved = contentPostRepository.findSavedByUserId(userId, PageRequest.of(0, COVER_TILES));
        List<Cover> allSavedCovers = recentSaved.getContent().stream()
                .filter(post -> post.getMediaUrl() != null && !post.getMediaUrl().isBlank())
                .map(post -> new Cover(post.getMediaUrl(), post.getMediaType()))
                .toList();

        List<SavedCollection> collections = collectionRepository.findByUser_IdOrderByUpdatedAtDesc(userId);
        Map<UUID, Long> counts = new HashMap<>();
        if (!collections.isEmpty()) {
            for (Object[] row : itemRepository.countVisibleByCollectionIds(
                    collections.stream().map(SavedCollection::getId).toList())) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        List<CollectionResponse> responses = collections.stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
        return new OverviewResponse(recentSaved.getTotalElements(), allSavedCovers, responses);
    }

    @Transactional
    public CollectionResponse create(UUID userId, String rawName, UUID postId) {
        String name = normalizeName(rawName);
        if (collectionRepository.countByUser_Id(userId) >= MAX_COLLECTIONS) {
            throw new BusinessException("COLLECTION_LIMIT",
                    "You can have up to " + MAX_COLLECTIONS + " collections.");
        }
        if (collectionRepository.existsByUser_IdAndNameIgnoreCase(userId, name)) {
            throw new BusinessException("COLLECTION_NAME_TAKEN", "You already have a collection with this name.");
        }
        SavedCollection collection = new SavedCollection();
        collection.setUser(userRepository.getReferenceById(userId));
        collection.setName(name);
        collection = collectionRepository.save(collection);
        log.info("Saved collection created user={} collection={}", userId, collection.getId());
        if (postId != null) {
            fileIn(userId, collection, postId);
        }
        return toResponse(collection, postId != null ? 1L : 0L);
    }

    @Transactional
    public CollectionResponse rename(UUID userId, UUID collectionId, String rawName) {
        SavedCollection collection = requireOwned(userId, collectionId);
        String name = normalizeName(rawName);
        if (!name.equalsIgnoreCase(collection.getName())
                && collectionRepository.existsByUser_IdAndNameIgnoreCaseAndIdNot(userId, name, collectionId)) {
            throw new BusinessException("COLLECTION_NAME_TAKEN", "You already have a collection with this name.");
        }
        collection.setName(name);
        collection.touch();
        long count = countVisible(collectionId);
        return toResponse(collection, count);
    }

    /** Deletes the collection only — its posts stay saved. */
    @Transactional
    public void delete(UUID userId, UUID collectionId) {
        SavedCollection collection = requireOwned(userId, collectionId);
        collectionRepository.delete(collection);
        log.info("Saved collection deleted user={} collection={}", userId, collectionId);
    }

    @Transactional(readOnly = true)
    public Page<ContentPostResponse> getPosts(UUID userId, UUID collectionId, Pageable pageable) {
        requireOwned(userId, collectionId);
        return contentPostService.getSavedCollectionPosts(userId, collectionId, pageable);
    }

    @Transactional
    public void addPost(UUID userId, UUID collectionId, UUID postId) {
        fileIn(userId, requireOwned(userId, collectionId), postId);
    }

    @Transactional
    public void removePost(UUID userId, UUID collectionId, UUID postId) {
        requireOwned(userId, collectionId);
        itemRepository.deleteFromCollection(collectionId, postId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getCollectionIdsForPost(UUID userId, UUID postId) {
        return itemRepository.findCollectionIdsForPost(userId, postId);
    }

    private void fileIn(UUID userId, SavedCollection collection, UUID postId) {
        ContentPost post = contentPostRepository.findById(postId)
                .filter(p -> Boolean.TRUE.equals(p.getIsPublic()) && p.getArchivedAt() == null)
                .orElseThrow(() -> new BusinessException("TARGET_NOT_FOUND", "Content not found."));
        // Filing a post saves it: a collection is a way to organise saved posts, never a second list.
        socialService.addFavorite(userId, ContentTargetType.POST, post.getId());
        if (!itemRepository.existsByCollection_IdAndPostId(collection.getId(), post.getId())) {
            SavedCollectionItem item = new SavedCollectionItem();
            item.setCollection(collection);
            item.setPostId(post.getId());
            itemRepository.save(item);
        }
        collection.touch();
    }

    private SavedCollection requireOwned(UUID userId, UUID collectionId) {
        return collectionRepository.findByIdAndUser_Id(collectionId, userId)
                .orElseThrow(() -> new BusinessException("COLLECTION_NOT_FOUND", "Collection not found."));
    }

    private long countVisible(UUID collectionId) {
        return itemRepository.countVisibleByCollectionIds(List.of(collectionId)).stream()
                .findFirst()
                .map(row -> (Long) row[1])
                .orElse(0L);
    }

    private CollectionResponse toResponse(SavedCollection collection, long itemCount) {
        List<Cover> covers = itemRepository.findCoverMedia(collection.getId(), PageRequest.of(0, COVER_TILES)).stream()
                .map(row -> new Cover((String) row[0], (String) row[1]))
                .toList();
        return new CollectionResponse(collection.getId(), collection.getName(), itemCount, covers, collection.getUpdatedAt());
    }

    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new BusinessException("COLLECTION_NAME_REQUIRED", "Give the collection a name.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("COLLECTION_NAME_TOO_LONG",
                    "Collection names are limited to " + MAX_NAME_LENGTH + " characters.");
        }
        return name;
    }
}
