package com.plateforme.ecosystem.dto;

public record BotResponseDto(
        String botMessage,
        /** True uniquement après POST /my-requests/{id}/confirm-bot-chat (passage à l’équipe). */
        boolean botConfirmed,
        String nextStep,
        /** Indication UX : la réponse IA contenait le marqueur de fin (le client doit encore valider via l’API). */
        boolean readyToConfirm
) {
}
