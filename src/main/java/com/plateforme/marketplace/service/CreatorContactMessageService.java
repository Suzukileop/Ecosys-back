package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.SendCreatorContactMessageRequest;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.service.ProfileExtensionsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorContactMessageService {

    static final int MAX_REQUESTS_PER_WINDOW = 5;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final CreatorProfileRepository creatorProfileRepository;
    private final MailDeliveryService mailDeliveryService;

    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public void sendContactMessage(UUID creatorUserId, SendCreatorContactMessageRequest request, String clientIp) {
        enforceRateLimit(clientIp, creatorUserId);

        CreatorProfile profile = creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));

        String destination = resolveDestinationEmail(profile);
        if (destination == null) {
            throw new BusinessException(
                    "CONTACT_EMAIL_UNAVAILABLE",
                    "This creator has no contact email configured.");
        }

        String senderName = request.name().trim();
        String senderEmail = request.email().trim();
        String subjectText = request.subject() != null && !request.subject().isBlank()
                ? request.subject().trim()
                : null;
        String messageText = request.message().trim();

        String mailSubject = "[Portfolio] " + (subjectText != null
                ? subjectText
                : "New message from " + senderName);

        String body = buildBody(senderName, senderEmail, subjectText, messageText, creatorUserId);

        mailDeliveryService.sendPlainText(destination, mailSubject, body);
        log.info("Portfolio contact message sent creatorId={} to={}", creatorUserId, destination);
    }

    private static String resolveDestinationEmail(CreatorProfile profile) {
        String fromList = ProfileExtensionsSupport.firstContactValue(profile.getContactEmails());
        if (fromList != null) {
            return fromList;
        }
        if (profile.getContactEmail() != null && !profile.getContactEmail().isBlank()) {
            return profile.getContactEmail().trim();
        }
        User user = profile.getUser();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        return null;
    }

    private static String buildBody(
            String senderName,
            String senderEmail,
            String subject,
            String message,
            UUID creatorUserId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You received a new message from your public portfolio contact form.\n\n");
        sb.append("From: ").append(senderName).append('\n');
        sb.append("Email: ").append(senderEmail).append('\n');
        sb.append("Subject: ").append(subject != null ? subject : "(none)").append('\n');
        sb.append("Creator ID: ").append(creatorUserId).append("\n\n");
        sb.append("Message:\n").append(message).append('\n');
        return sb.toString();
    }

    void enforceRateLimit(String clientIp, UUID creatorUserId) {
        String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        String key = ip + ":" + creatorUserId;
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_LIMIT_WINDOW.toMillis();

        Deque<Long> timestamps = rateLimitBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                throw new BusinessException(
                        "RATE_LIMIT_EXCEEDED",
                        "Too many contact messages. Please try again later.");
            }
            timestamps.addLast(now);
        }
    }
}
