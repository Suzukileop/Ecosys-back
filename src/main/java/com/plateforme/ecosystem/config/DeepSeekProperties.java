package com.plateforme.ecosystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        String apiUrl,
        String apiKey,
        String model,
        int maxTokens,
        double temperature,
        /**
         * Si true et clé absente : réponse texte de secours (développement) au lieu d'une erreur 503.
         */
        boolean stubWithoutKey
) {
}
