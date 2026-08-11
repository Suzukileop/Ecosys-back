package com.plateforme.shared.service;

import com.plateforme.shared.dto.LandingFeedbackRequest;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LandingFeedbackService {

    static final int MAX_REQUESTS_PER_WINDOW = 5;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final MailDeliveryService mailDeliveryService;

    @Value("${app.landing.feedback-email:leopardjuliocesar8@gmail.com}")
    private String feedbackEmail;

    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();

    public void sendFeedback(LandingFeedbackRequest request, String clientIp) {
        enforceRateLimit(clientIp);

        String destination = feedbackEmail == null ? "" : feedbackEmail.trim();
        if (destination.isBlank()) {
            throw new BusinessException(
                    "FEEDBACK_EMAIL_UNAVAILABLE",
                    "Feedback destination is not configured.");
        }

        String senderName = request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : "Anonymous";
        String senderEmail = request.email().trim();
        String messageText = request.message().trim();

        String subject = "[Landing feedback] from " + senderName;
        String body = buildBody(senderName, senderEmail, messageText);

        mailDeliveryService.sendPlainText(destination, subject, body);
        log.info("Landing feedback sent to={}", destination);
    }

    private static String buildBody(String senderName, String senderEmail, String message) {
        return """
                You received new feedback from the NoProbleme landing page.

                From: %s
                Email: %s

                Message:
                %s
                """.formatted(senderName, senderEmail, message);
    }

    void enforceRateLimit(String clientIp) {
        String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_LIMIT_WINDOW.toMillis();

        Deque<Long> timestamps = rateLimitBuckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                throw new BusinessException(
                        "RATE_LIMIT_EXCEEDED",
                        "Too many feedback messages. Please try again later.");
            }
            timestamps.addLast(now);
        }
    }
}
