package com.plateforme.shared.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JavaMailDeliveryServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private JavaMailDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new JavaMailDeliveryService(mailSender);
        ReflectionTestUtils.setField(service, "appMailFrom", "noreply@test.com");
        ReflectionTestUtils.setField(service, "springMailUsername", "");
    }

    @Test
    @DisplayName("sendPlainText : construit et envoie un MimeMessage")
    void sendPlainText_sendsMimeMessage() throws Exception {
        Properties props = new Properties();
        Session session = Session.getInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        service.sendPlainText("to@test.com", "Subject", "Body");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPlainText : sans expéditeur configuré, aucun envoi")
    void sendPlainText_skipsWhenNoFromConfigured() {
        ReflectionTestUtils.setField(service, "appMailFrom", "");
        ReflectionTestUtils.setField(service, "springMailUsername", "");

        service.sendPlainText("to@test.com", "S", "B");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPlainText : utilise spring.mail.username si app.mail.from vide")
    void sendPlainText_usesSpringMailUsernameWhenAppFromBlank() throws Exception {
        ReflectionTestUtils.setField(service, "appMailFrom", "");
        ReflectionTestUtils.setField(service, "springMailUsername", "smtp-user@test.com");

        Properties props = new Properties();
        Session session = Session.getInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        service.sendPlainText("to@test.com", "Subject", "Body");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
