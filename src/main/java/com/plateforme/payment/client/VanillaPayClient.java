package com.plateforme.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client HTTP pour l'API Vanilla Pay International (VPI) v2.
 * Doc / SDK : https://www.npmjs.com/package/@mandarvl/vanilla-pay-international
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VanillaPayClient {

    private static final String API_VERSION = "v2";

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${vpi.client-id:}")
    private String clientId;

    @Value("${vpi.client-secret:}")
    private String clientSecret;

    @Value("${vpi.key-secret:}")
    private String keySecret;

    @Value("${vpi.environment:PREPROD}")
    private String environment;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }

    public VpiPaymentInitResult initiatePayment(VpiPaymentInitRequest request) {
        ensureConfigured();
        String token = getAccessToken();
        WebClient client = baseClient(token);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("montant", request.amount());
        payload.put("reference", request.reference());
        payload.put("panier", request.cart());
        payload.put("notif_url", request.callbackUrl());
        payload.put("redirect_url", request.redirectUrl());
        payload.put("devise", request.currency());
        payload.put("mode_paiement", request.paymentMethod());

        try {
            JsonNode root = client.post()
                    .uri("/api/webpayment/" + API_VERSION + "/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = requireData(root, "initiate");
            String url = data.path("url").asText(null);
            if (url == null || url.isBlank()) {
                throw new BusinessException("VPI_INIT_FAILED", "URL de paiement VPI absente");
            }
            String id = extractPaymentId(url);
            return new VpiPaymentInitResult(id, url);
        } catch (WebClientResponseException e) {
            log.error("VPI initiate HTTP {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("VPI_INIT_FAILED", "Impossible d'initialiser le paiement VPI");
        }
    }

    public VpiPaymentDetails getPaymentStatus(String paymentId, String paymentMethod) {
        ensureConfigured();
        String token = getAccessToken();
        String decodedId = decodePaymentIdIfNeeded(paymentId);
        WebClient client = baseClient(token);

        try {
            JsonNode root = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/webpayment/" + API_VERSION + "/status/{id}")
                            .queryParam("mode_paiement", paymentMethod)
                            .build(decodedId))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = requireData(root, "status");
            return mapPaymentDetails(data, paymentId);
        } catch (WebClientResponseException e) {
            log.error("VPI status HTTP {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("VPI_STATUS_FAILED", "Impossible de vérifier le paiement VPI");
        }
    }

    public boolean validateWebhookSignature(String vpiSignature, Map<String, String> body) {
        if (vpiSignature == null || vpiSignature.isBlank() || keySecret == null || keySecret.isBlank()) {
            return false;
        }
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            String hashed = hmacSha256Hex(keySecret, bodyJson);
            return hashed.equalsIgnoreCase(vpiSignature.trim());
        } catch (JsonProcessingException e) {
            log.warn("VPI webhook : sérialisation payload impossible");
            return false;
        }
    }

    public VpiPaymentDetails mapWebhookPayload(Map<String, String> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reference_VPI", body.get("reference_VPI"));
        data.put("panier", body.get("panier"));
        data.put("reference", body.get("reference"));
        data.put("montant", parseLong(body.get("montant")));
        data.put("montantRecu", parseLong(body.get("montantRecu")));
        data.put("etat", body.get("etat"));
        data.put("referenceMM", body.get("referenceMM"));
        data.put("initiateur", body.get("initiateur"));
        try {
            return mapPaymentDetails(objectMapper.valueToTree(data), body.getOrDefault("reference", ""));
        } catch (Exception e) {
            throw new BusinessException("VPI_WEBHOOK_INVALID", "Payload webhook VPI invalide");
        }
    }

    private WebClient baseClient(String token) {
        return webClientBuilder.clone()
                .baseUrl(baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, token)
                .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .defaultHeader(HttpHeaders.DATE, Instant.now().toString())
                .build();
    }

    private String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            WebClient client = webClientBuilder.clone()
                    .baseUrl(baseUrl())
                    .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .build();

            try {
                JsonNode root = client.get()
                        .uri("/webpayment/token")
                        .header("Client-Id", clientId)
                        .header("Client-Secret", clientSecret)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                JsonNode data = requireData(root, "token");
                String token = data.path("Token").asText(null);
                if (token == null || token.isBlank()) {
                    throw new BusinessException("VPI_AUTH_FAILED", "Token VPI absent");
                }
                cachedToken = token;
                tokenExpiresAt = Instant.now().plusSeconds(50 * 60);
                return token;
            } catch (WebClientResponseException e) {
                log.error("VPI auth HTTP {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BusinessException("VPI_AUTH_FAILED", "Authentification VPI échouée");
            }
        }
    }

    private String baseUrl() {
        return "PROD".equalsIgnoreCase(environment)
                ? "https://bo.vanilla-pay.net"
                : "https://preprod.vanilla-pay.net";
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException("VPI_NOT_CONFIGURED", "Vanilla Pay International non configuré");
        }
    }

    private static JsonNode requireData(JsonNode root, String step) {
        if (root == null) {
            throw new BusinessException("VPI_ERROR", "Réponse VPI vide (" + step + ")");
        }
        int code = root.path("CodeRetour").asInt(-1);
        if (code != 0 && code != 200) {
            String detail = root.path("DescRetour").asText(root.path("DetailRetour").asText("Erreur VPI"));
            throw new BusinessException("VPI_ERROR", detail);
        }
        JsonNode data = root.path("Data");
        if (data.isMissingNode() || data.isNull()) {
            throw new BusinessException("VPI_ERROR", "Données VPI absentes (" + step + ")");
        }
        return data;
    }

    private static String extractPaymentId(String url) {
        int idx = url.indexOf("id=");
        if (idx >= 0) {
            return url.substring(idx + 3);
        }
        return url;
    }

    private static String decodePaymentIdIfNeeded(String idOrRef) {
        String[] parts = idOrRef.split("\\.");
        if (parts.length != 3) {
            return idOrRef;
        }
        try {
            String payloadB64 = parts[1].replace('-', '+').replace('_', '/');
            while (payloadB64.length() % 4 != 0) {
                payloadB64 += "=";
            }
            return new String(java.util.Base64.getDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return idOrRef;
        }
    }

    private static VpiPaymentDetails mapPaymentDetails(JsonNode data, String id) {
        String status = data.path("etat").asText("").trim();
        String paymentMethod = data.hasNonNull("referenceMM") && !data.path("referenceMM").asText("").isBlank()
                ? "mobile_money"
                : "international";
        return new VpiPaymentDetails(
                id,
                data.path("reference").asText(null),
                data.path("reference_VPI").asText(null),
                status,
                paymentMethod,
                data.path("montant").asLong(0),
                data.path("initiateur").asText(null)
        );
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hmacSha256Hex(String key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new BusinessException("VPI_SIGNATURE_ERROR", "Calcul signature VPI impossible");
        }
    }

    public record VpiPaymentInitRequest(
            long amount,
            String reference,
            String cart,
            String currency,
            String paymentMethod,
            String callbackUrl,
            String redirectUrl
    ) {}

    public record VpiPaymentInitResult(String id, String url) {}

    public record VpiPaymentDetails(
            String id,
            String reference,
            String vpiReference,
            String status,
            String paymentMethod,
            long amount,
            String phoneNumber
    ) {
        public boolean isSuccessful() {
            return "SUCCESS".equalsIgnoreCase(status);
        }

        public boolean isFailed() {
            return "FAILED".equalsIgnoreCase(status);
        }
    }
}
