package com.plateforme.marketplace.service;

import com.plateforme.marketplace.entity.*;
import com.plateforme.marketplace.repository.MarketplaceBundleItemRepository;
import com.plateforme.marketplace.repository.MarketplaceBundleRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplacePurchaseRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplacePurchaseServiceTest {

    @Mock
    private MarketplacePurchaseRepository purchaseRepository;

    @Mock
    private MarketplaceProductService productService;

    @Mock
    private MarketplaceProductRepository productRepository;

    @Mock
    private MarketplaceBundleRepository bundleRepository;

    @Mock
    private MarketplaceBundleItemRepository bundleItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MarketplacePurchaseService purchaseService;

    private UUID buyerId;
    private UUID productId;
    private UUID bundleId;
    private User buyer;
    private User creator;
    private MarketplaceProduct product;
    private MarketplaceBundle bundle;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        bundleId = UUID.randomUUID();
        buyer = new User();
        buyer.setId(buyerId);
        buyer.setFullName("Buyer");

        creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setFullName("Creator");

        product = new MarketplaceProduct();
        product.setId(productId);
        product.setTitle("Ebook");
        product.setType(ProductType.EBOOK);
        product.setPriceCents(500);
        product.setCurrency("EUR");
        product.setIsPublished(true);
        product.setCreator(creator);
        product.setSalesCount(0);

        bundle = new MarketplaceBundle();
        bundle.setId(bundleId);
        bundle.setTitle("Bundle Pack");
        bundle.setPriceCents(1200);
        bundle.setCurrency("EUR");
        bundle.setIsPublished(true);
        bundle.setCreator(creator);
    }

    @Test
    @DisplayName("simulatePurchase : déjà acheté → BusinessException")
    void simulatePurchase_alreadyOwned() {
        when(userRepository.findByIdAndDeletedAtIsNull(buyerId)).thenReturn(Optional.of(buyer));
        when(productService.requirePublishedProduct(productId)).thenReturn(product);
        when(purchaseRepository.existsByBuyer_IdAndProduct_IdAndPaymentStatus(
                buyerId, productId, MarketplacePurchaseStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(() -> purchaseService.simulatePurchase(buyerId, productId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ALREADY_PURCHASED");

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("simulatePurchase : succès avec notifications")
    void simulatePurchase_success() {
        when(userRepository.findByIdAndDeletedAtIsNull(buyerId)).thenReturn(Optional.of(buyer));
        when(productService.requirePublishedProduct(productId)).thenReturn(product);
        when(purchaseRepository.existsByBuyer_IdAndProduct_IdAndPaymentStatus(
                buyerId, productId, MarketplacePurchaseStatus.COMPLETED)).thenReturn(false);
        when(purchaseRepository.save(any(MarketplacePurchase.class))).thenAnswer(inv -> {
            MarketplacePurchase p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var response = purchaseService.simulatePurchase(buyerId, productId);

        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.paymentStatus()).isEqualTo(MarketplacePurchaseStatus.COMPLETED);
        verify(purchaseRepository).save(any(MarketplacePurchase.class));
        verify(notificationService, times(2)).createAndSend(
                any(UUID.class), anyString(), anyString(), anyString(), eq("PLATFORM"), any(), any());
    }

    @Test
    @DisplayName("simulateBundlePurchase : crée achat bundle et produits")
    void simulateBundlePurchase_success() {
        UUID product2Id = UUID.randomUUID();
        MarketplaceProduct product2 = new MarketplaceProduct();
        product2.setId(product2Id);
        product2.setTitle("Template");
        product2.setCreator(creator);

        MarketplaceBundleItem item1 = new MarketplaceBundleItem();
        item1.setProduct(product);
        MarketplaceBundleItem item2 = new MarketplaceBundleItem();
        item2.setProduct(product2);

        when(userRepository.findByIdAndDeletedAtIsNull(buyerId)).thenReturn(Optional.of(buyer));
        when(bundleRepository.findByIdAndIsPublishedTrue(bundleId)).thenReturn(Optional.of(bundle));
        when(purchaseRepository.existsByBuyer_IdAndBundle_IdAndPaymentStatus(
                buyerId, bundleId, MarketplacePurchaseStatus.COMPLETED)).thenReturn(false);
        when(bundleItemRepository.findByBundle_IdOrderBySortOrderAsc(bundleId))
                .thenReturn(List.of(item1, item2));
        when(purchaseRepository.existsByBuyer_IdAndProduct_IdAndPaymentStatus(
                eq(buyerId), any(UUID.class), eq(MarketplacePurchaseStatus.COMPLETED))).thenReturn(false);
        when(purchaseRepository.save(any(MarketplacePurchase.class))).thenAnswer(inv -> {
            MarketplacePurchase p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var response = purchaseService.simulateBundlePurchase(buyerId, bundleId);

        assertThat(response.bundleId()).isEqualTo(bundleId);
        verify(purchaseRepository, times(3)).save(any(MarketplacePurchase.class));
        verify(notificationService, times(2)).createAndSend(
                any(UUID.class), anyString(), anyString(), anyString(), eq("PLATFORM"), any(), any());
    }

    @Test
    @DisplayName("simulateBundlePurchase : déjà acheté → BusinessException")
    void simulateBundlePurchase_alreadyOwned() {
        when(userRepository.findByIdAndDeletedAtIsNull(buyerId)).thenReturn(Optional.of(buyer));
        when(bundleRepository.findByIdAndIsPublishedTrue(bundleId)).thenReturn(Optional.of(bundle));
        when(purchaseRepository.existsByBuyer_IdAndBundle_IdAndPaymentStatus(
                buyerId, bundleId, MarketplacePurchaseStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(() -> purchaseService.simulateBundlePurchase(buyerId, bundleId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ALREADY_PURCHASED");
    }
}
