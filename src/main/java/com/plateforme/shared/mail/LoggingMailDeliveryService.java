package com.plateforme.shared.mail;

import com.plateforme.shared.util.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(JavaMailDeliveryService.class)
@Slf4j
public class LoggingMailDeliveryService implements MailDeliveryService {

    /** Local development only (MAIL_LOG_BODY=true): lets you read verification codes without SMTP. */
    @Value("${app.mail.log-body:false}")
    private boolean logBody;

    @Override
    public void sendPlainText(String to, String subject, String body) {
        if (logBody) {
            log.info("Mail non envoyé (pas de SMTP) — contenu pour le développement. to={} subject={}\n{}",
                    LogMasking.email(to), subject, body);
            return;
        }
        log.debug("Mail non envoyé (configurez spring.mail.host / MAIL_HOST). to={} subject={}",
                LogMasking.email(to), subject);
    }
}
