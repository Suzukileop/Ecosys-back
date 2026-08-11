package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.SendCreatorContactMessageRequest;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorContactMessageServiceTest {

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @Mock
    private MailDeliveryService mailDeliveryService;

    @InjectMocks
    private CreatorContactMessageService service;

    private UUID creatorId;
    private User creatorUser;
    private CreatorProfile profile;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        creatorUser = new User();
        creatorUser.setId(creatorId);
        creatorUser.setEmail("account@creator.test");

        profile = new CreatorProfile();
        profile.setUser(creatorUser);
        profile.setContactEmail("portfolio@creator.test");
    }

    @Test
    @DisplayName("sends mail to profile contactEmail on happy path")
    void sendContactMessage_happyPath_usesContactEmail() {
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));

        SendCreatorContactMessageRequest request = new SendCreatorContactMessageRequest(
                "Alice Visitor",
                "alice@example.com",
                "Collab inquiry",
                "Hello, I would like to work with you.");

        service.sendContactMessage(creatorId, request, "203.0.113.10");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailDeliveryService).sendPlainText(
                eq("portfolio@creator.test"),
                subjectCaptor.capture(),
                bodyCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("[Portfolio] Collab inquiry");
        assertThat(bodyCaptor.getValue())
                .contains("From: Alice Visitor")
                .contains("Email: alice@example.com")
                .contains("Subject: Collab inquiry")
                .contains("Creator ID: " + creatorId)
                .contains("Hello, I would like to work with you.");
    }

    @Test
    @DisplayName("falls back to account email when contactEmail is blank")
    void sendContactMessage_fallsBackToAccountEmail() {
        profile.setContactEmail("  ");
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));

        SendCreatorContactMessageRequest request = new SendCreatorContactMessageRequest(
                "Bob",
                "bob@example.com",
                null,
                "Just saying hi");

        service.sendContactMessage(creatorId, request, "203.0.113.11");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailDeliveryService).sendPlainText(
                eq("account@creator.test"),
                subjectCaptor.capture(),
                any());
        assertThat(subjectCaptor.getValue()).isEqualTo("[Portfolio] New message from Bob");
    }

    @Test
    @DisplayName("prefers first contactEmails list entry over legacy contactEmail")
    void sendContactMessage_prefersContactEmailsList() {
        profile.setContactEmail("legacy@creator.test");
        profile.setContactEmails(List.of(
                new ProfileContactEntryDto(UUID.randomUUID(), 0, "list@creator.test"),
                new ProfileContactEntryDto(UUID.randomUUID(), 1, "second@creator.test")));
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));

        SendCreatorContactMessageRequest request = new SendCreatorContactMessageRequest(
                "Eve",
                "eve@example.com",
                "Hello",
                "Body");

        service.sendContactMessage(creatorId, request, "203.0.113.13");

        verify(mailDeliveryService).sendPlainText(eq("list@creator.test"), any(), any());
    }

    @Test
    @DisplayName("throws when no destination email is available")
    void sendContactMessage_noDestination_throws() {
        profile.setContactEmail(null);
        creatorUser.setEmail(null);
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));

        SendCreatorContactMessageRequest request = new SendCreatorContactMessageRequest(
                "Carol",
                "carol@example.com",
                "Hi",
                "Message body");

        assertThatThrownBy(() -> service.sendContactMessage(creatorId, request, "203.0.113.12"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("CONTACT_EMAIL_UNAVAILABLE");

        verify(mailDeliveryService, never()).sendPlainText(any(), any(), any());
    }

    @Test
    @DisplayName("rate limits after 5 messages per IP+creator within window")
    void sendContactMessage_rateLimit_throws429Code() {
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));

        SendCreatorContactMessageRequest request = new SendCreatorContactMessageRequest(
                "Dana",
                "dana@example.com",
                "Subject",
                "Body");
        String ip = "198.51.100.20";

        for (int i = 0; i < CreatorContactMessageService.MAX_REQUESTS_PER_WINDOW; i++) {
            service.sendContactMessage(creatorId, request, ip);
        }

        assertThatThrownBy(() -> service.sendContactMessage(creatorId, request, ip))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("RATE_LIMIT_EXCEEDED");

        verify(mailDeliveryService, times(CreatorContactMessageService.MAX_REQUESTS_PER_WINDOW))
                .sendPlainText(any(), any(), any());
    }
}
