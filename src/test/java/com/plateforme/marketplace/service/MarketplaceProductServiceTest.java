package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.MarketplaceProductRequest;
import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.DemoType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.ProductType;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceProductServiceTest {

    @Mock
    private MarketplaceProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketplaceProductService productService;

    private UUID creatorId;
    private User creator;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        creator = new User();
        creator.setId(creatorId);
        creator.setFullName("Creator");
    }

    @Test
    @DisplayName("createProduct : preview limit invalide → BusinessException")
    void createProduct_invalidPreviewLimit() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));

        MarketplaceProductRequest req = sampleRequest(150);

        assertThatThrownBy(() -> productService.createProduct(creatorId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_PREVIEW_LIMIT");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPublishedProduct : produit non publié → BusinessException")
    void getPublishedProduct_notFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndIsPublishedTrue(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getPublishedProduct(productId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("deleteProduct : soft delete")
    void deleteProduct_softDelete() {
        UUID productId = UUID.randomUUID();
        MarketplaceProduct product = new MarketplaceProduct();
        product.setId(productId);
        product.setCreator(creator);
        product.setIsPublished(true);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.deleteProduct(creatorId, productId);

        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(product.getIsPublished()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("setPublished : publie sans fichier principal")
    void setPublished_success() {
        UUID productId = UUID.randomUUID();
        MarketplaceProduct product = new MarketplaceProduct();
        product.setId(productId);
        product.setCreator(creator);
        product.setIsPublished(false);
        product.setTitle("Title");
        product.setType(ProductType.PDF);
        product.setPriceCents(1000);
        product.setCurrency("EUR");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenAnswer(inv -> inv.getArgument(0));

        var result = productService.setPublished(creatorId, productId, true);

        assertThat(product.getIsPublished()).isTrue();
        assertThat(result.isPublished()).isTrue();
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("createProduct : produit gratuit avec prix barré → BusinessException")
    void createProduct_freeWithComparePrice() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));

        MarketplaceProductRequest req = new MarketplaceProductRequest(
                ProductType.PDF,
                "Title",
                "Description",
                0,
                "EUR",
                "Tech",
                null,
                "https://thumb.com/x.jpg",
                DemoType.NONE,
                null,
                null,
                List.of(),
                List.of(),
                DeliveryMode.BOTH,
                List.of(),
                "PDF",
                5,
                "EN",
                "1.0",
                null,
                5,
                List.of("tag"),
                500,
                null,
                null,
                false,
                false,
                List.of()
        );

        assertThatThrownBy(() -> productService.createProduct(creatorId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_COMPARE_PRICE");

        verify(productRepository, never()).save(any());
    }

    private MarketplaceProductRequest sampleRequest(Integer previewLimit) {
        return new MarketplaceProductRequest(
                ProductType.PDF,
                "Title",
                "Description",
                1000,
                "EUR",
                "Tech",
                null,
                "https://thumb.com/x.jpg",
                DemoType.NONE,
                null,
                null,
                List.of(),
                List.of(),
                DeliveryMode.BOTH,
                List.of(),
                "PDF",
                5,
                "EN",
                "1.0",
                previewLimit,
                5,
                List.of("tag"),
                null,
                null,
                null,
                false,
                false,
                List.of()
        );
    }
}
