package com.plateforme.shared.mail;

/**
 * Envoi d'e-mails transactionnels (notifications, récap, etc.).
 */
public interface MailDeliveryService {

    /**
     * Envoie un message texte brut (UTF-8). Implémentations souvent asynchrones.
     */
    void sendPlainText(String to, String subject, String body);
}
