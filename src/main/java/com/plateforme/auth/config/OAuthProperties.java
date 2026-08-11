package com.plateforme.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private Provider google = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
    }

    public boolean isGoogleConfigured() {
        return isConfigured(google);
    }

    private boolean isConfigured(Provider provider) {
        return provider.getClientId() != null && !provider.getClientId().isBlank()
                && provider.getClientSecret() != null && !provider.getClientSecret().isBlank()
                && provider.getRedirectUri() != null && !provider.getRedirectUri().isBlank();
    }
}
