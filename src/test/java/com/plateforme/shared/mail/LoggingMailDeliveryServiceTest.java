package com.plateforme.shared.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingMailDeliveryServiceTest {

    @Test
    @DisplayName("sendPlainText : ne lève pas d’exception (mode sans SMTP)")
    void sendPlainText_doesNotThrow() {
        new LoggingMailDeliveryService().sendPlainText("a@b.com", "subject", "body");
    }
}
