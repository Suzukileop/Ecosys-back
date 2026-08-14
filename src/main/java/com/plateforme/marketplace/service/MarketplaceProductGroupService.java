package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ProductGroupRequest;
import com.plateforme.marketplace.dto.ProductGroupResponse;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplaceProductGroup;
import com.plateforme.marketplace.entity.MarketplaceProductGroupItem;
import com.plateforme.marketplace.repository.MarketplaceProductGroupItemRepository;
import com.plateforme.marketplace.repository.MarketplaceProductGroupRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceProductGroupService {

    private final MarketplaceProductGroupRepository groupRepository;
    private final MarketplaceProductGroupItemRepository groupItemRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductGroupResponse createGroup(UUID creatorId, ProductGroupRequest req) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + creatorId));

        String name = normalizeName(req.name());
        assertUniqueName(creatorId, name, null);

        MarketplaceProductGroup group = new MarketplaceProductGroup();
        group.setCreator(creator);
        group.setName(name);
        group.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        group = groupRepository.save(group);
        replaceItems(group, creatorId, req.productIds());

        log.info("Product group created id={} creator={}", group.getId(), creatorId);
        return toResponse(group);
    }

    @Transactional
    public ProductGroupResponse updateGroup(UUID creatorId, UUID groupId, ProductGroupRequest req) {
        MarketplaceProductGroup group = requireOwnedGroup(creatorId, groupId);
        String name = normalizeName(req.name());
        assertUniqueName(creatorId, name, groupId);

        group.setName(name);
        if (req.sortOrder() != null) {
            group.setSortOrder(req.sortOrder());
        }
        group = groupRepository.save(group);
        replaceItems(group, creatorId, req.productIds());

        log.info("Product group updated id={} creator={}", groupId, creatorId);
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public ProductGroupResponse getMyGroup(UUID creatorId, UUID groupId) {
        return toResponse(requireOwnedGroup(creatorId, groupId));
    }

    @Transactional(readOnly = true)
    public Page<ProductGroupResponse> getMyGroups(UUID creatorId, Pageable pageable) {
        return groupRepository.findByCreator_IdOrderBySortOrderAscCreatedAtAsc(creatorId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteGroup(UUID creatorId, UUID groupId) {
        MarketplaceProductGroup group = requireOwnedGroup(creatorId, groupId);
        group.setDeletedAt(LocalDateTime.now());
        groupRepository.save(group);
        log.info("Product group soft-deleted id={} creator={}", groupId, creatorId);
    }

    @Transactional(readOnly = true)
    public Page<ProductGroupResponse> getPublicGroups(UUID creatorId, Pageable pageable) {
        userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + creatorId));
        return groupRepository.findByCreator_IdOrderBySortOrderAscCreatedAtAsc(creatorId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductGroupResponse getPublicGroup(UUID creatorId, UUID groupId) {
        MarketplaceProductGroup group = groupRepository.findByIdAndCreator_Id(groupId, creatorId)
                .orElseThrow(() -> new BusinessException("PRODUCT_GROUP_NOT_FOUND",
                        "Product group not found: " + groupId));
        return toResponse(group);
    }

    private void replaceItems(MarketplaceProductGroup group, UUID creatorId, List<UUID> productIds) {
        group.getItems().clear();
        groupItemRepository.deleteByProductGroup_Id(group.getId());

        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(productIds);
        int order = 0;
        for (UUID productId : uniqueIds) {
            MarketplaceProduct product = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                            "Product not found: " + productId));
            UUID ownerId = product.getCreator() != null ? product.getCreator().getId() : null;
            if (!Objects.equals(ownerId, creatorId)) {
                throw new AccessDeniedException("Product " + productId + " does not belong to the current user");
            }
            MarketplaceProductGroupItem item = new MarketplaceProductGroupItem();
            item.setProductGroup(group);
            item.setProduct(product);
            item.setSortOrder(order++);
            group.getItems().add(item);
        }
        groupRepository.save(group);
    }

    private void assertUniqueName(UUID creatorId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? groupRepository.existsByCreator_IdAndNameIgnoreCase(creatorId, name)
                : groupRepository.existsByCreator_IdAndNameIgnoreCaseAndIdNot(creatorId, name, excludeId);
        if (exists) {
            throw new BusinessException("PRODUCT_GROUP_NAME_TAKEN",
                    "A product group with this name already exists");
        }
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("PRODUCT_GROUP_NAME_REQUIRED", "Group name is required");
        }
        return trimmed;
    }

    private MarketplaceProductGroup requireOwnedGroup(UUID creatorId, UUID groupId) {
        return groupRepository.findByIdAndCreator_Id(groupId, creatorId)
                .orElseThrow(() -> new BusinessException("PRODUCT_GROUP_NOT_FOUND",
                        "Product group not found: " + groupId));
    }

    private ProductGroupResponse toResponse(MarketplaceProductGroup group) {
        List<UUID> productIds = groupItemRepository.findByProductGroup_IdOrderBySortOrderAsc(group.getId())
                .stream()
                .map(item -> item.getProduct().getId())
                .toList();

        return new ProductGroupResponse(
                group.getId(),
                group.getCreator().getId(),
                group.getName(),
                group.getSortOrder() != null ? group.getSortOrder() : 0,
                productIds.size(),
                new ArrayList<>(productIds),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
