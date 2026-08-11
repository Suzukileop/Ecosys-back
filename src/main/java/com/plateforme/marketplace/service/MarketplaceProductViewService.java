package com.plateforme.marketplace.service;

import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplaceProductView;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplaceProductViewRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceProductViewService {

    private final MarketplaceProductViewRepository viewRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Records one view per authenticated client account per product.
     * Creators viewing their own product are ignored.
     */
    @Transactional
    public boolean recordView(UUID userId, UUID productId) {
        MarketplaceProduct product = productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found."));

        if (product.getCreator().getId().equals(userId)) {
            return false;
        }

        if (viewRepository.existsByProduct_IdAndUser_Id(productId, userId)) {
            return false;
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));

        MarketplaceProductView view = new MarketplaceProductView();
        view.setProduct(product);
        view.setUser(user);
        viewRepository.save(view);

        int current = product.getViews() != null ? product.getViews() : 0;
        product.setViews(current + 1);
        productRepository.save(product);
        log.debug("Product view recorded product={} user={}", productId, userId);
        return true;
    }
}
