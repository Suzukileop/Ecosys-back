package com.plateforme.shared.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(JavaMailDeliveryService.class)
@Slf4j
public class LoggingMailDeliveryService implements MailDeliveryService {

    @Override
    public void sendPlainText(String to, String subject, String body) {
        log.debug("Mail non envoyé (configurez spring.mail.host / MAIL_HOST). to={} subject={}", to, subject);
    }
}
