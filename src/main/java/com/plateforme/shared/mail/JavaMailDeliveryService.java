package com.plateforme.shared.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
@Slf4j
public class JavaMailDeliveryService implements MailDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String appMailFrom;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    @Async
    @Override
    public void sendPlainText(String to, String subject, String body) {
        String from = resolveFrom();
        if (from.isBlank()) {
            log.error(
                    "Email non envoyé : configurez app.mail.from (MAIL_FROM) ou spring.mail.username comme expéditeur. to={} subject={}",
                    to,
                    subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(msg);
            log.info("Email envoyé à {} sujet={}", to, subject);
        } catch (Exception e) {
            log.error("Échec envoi email à {} sujet={}", to, subject, e);
        }
    }

    private String resolveFrom() {
        if (appMailFrom != null && !appMailFrom.isBlank()) {
            return appMailFrom.trim();
        }
        if (springMailUsername != null && !springMailUsername.isBlank()) {
            return springMailUsername.trim();
        }
        return "";
    }
}
