package com.plateforme.marketplace.service;

import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.marketplace.entity.AccessMode;
import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplacePurchase;
import com.plateforme.marketplace.entity.MarketplacePurchaseStatus;
import com.plateforme.marketplace.repository.ContentAccessLogRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplacePurchaseRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceAccessServiceTest {

    @Mock
    private MarketplacePurchaseService purchaseService;

    @Mock
    private MarketplaceProductRepository productRepository;

    @Mock
    private MarketplacePurchaseRepository purchaseRepository;

    @Mock
    private ContentAccessLogRepository accessLogRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private MarketplaceAccessService accessService;

    private UUID buyerId;
    private UUID purchaseId;
    private MarketplacePurchase purchase;
    private MarketplaceProduct product;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        purchaseId = UUID.randomUUID();

        User buyer = new User();
        buyer.setId(buyerId);

        product = new MarketplaceProduct();
        product.setId(UUID.randomUUID());
        product.setMainFileR2Key("marketplace/private/file.pdf");
        product.setDeliveryMode(DeliveryMode.BOTH);
        product.setMaxDownloads(5);

        purchase = new MarketplacePurchase();
        purchase.setId(purchaseId);
        purchase.setBuyer(buyer);
        purchase.setProduct(product);
        purchase.setPaymentStatus(MarketplacePurchaseStatus.COMPLETED);
        purchase.setDownloadCount(0);
    }

    @Test
    @DisplayName("getAccessUrl : stream-only + download → BusinessException")
    void getAccessUrl_streamOnlyRejectsDownload() {
        product.setDeliveryMode(DeliveryMode.STREAM_ONLY);
        when(purchaseService.requireOwnedPurchase(buyerId, purchaseId)).thenReturn(purchase);

        assertThatThrownBy(() -> accessService.getAccessUrl(
                buyerId, purchaseId, AccessMode.DOWNLOAD, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DOWNLOAD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("getAccessUrl : download incrémente le compteur")
    void getAccessUrl_downloadIncrementsCount() throws Exception {
        when(purchaseService.requireOwnedPurchase(buyerId, purchaseId)).thenReturn(purchase);
        when(storageService.generateSignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://signed.url/file?disposition=attachment");

        var response = accessService.getAccessUrl(
                buyerId, purchaseId, AccessMode.DOWNLOAD, "127.0.0.1", "JUnit");

        assertThat(response.url()).isEqualTo("https://signed.url/file?disposition=attachment");
        assertThat(response.accessMode()).isEqualTo(AccessMode.DOWNLOAD);
        assertThat(response.filename()).isEqualTo("file.pdf");
        assertThat(purchase.getDownloadCount()).isEqualTo(1);
        verify(purchaseRepository).save(purchase);
        verify(accessLogRepository).save(any());
    }
}
