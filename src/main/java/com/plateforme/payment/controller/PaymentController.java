package com.plateforme.payment.controller;

import com.plateforme.payment.dto.CheckoutUrlDto;
import com.plateforme.payment.service.PaymentService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Paiements Vanilla Pay International — écosystème")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Créer une session de paiement VPI pour une demande niche validée")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL de paiement VPI"),
            @ApiResponse(responseCode = "400", description = "Demande non éligible")
    })
    @PostMapping("/ecosystem/{requestId}/checkout")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CheckoutUrlDto> createCheckout(@PathVariable UUID requestId) {
        UUID clientId = getCurrentUserId();
        String url = paymentService.createCheckoutSession(requestId, clientId);
        return ResponseEntity.ok(new CheckoutUrlDto(url));
    }

    @Operation(summary = "Confirmer un paiement VPI (retour utilisateur ou polling)")
    @PostMapping("/ecosystem/{requestId}/confirm")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> confirmPayment(@PathVariable UUID requestId) {
        paymentService.confirmEcosystemPayment(requestId, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Webhook VPI (notification serveur-à-serveur)")
    @PostMapping(value = "/webhook/vpi", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> vpiWebhook(
            @RequestHeader(value = "VPI-Signature", required = false) String vpiSignature,
            @RequestParam MultiValueMap<String, String> formParams) {
        Map<String, String> body = new LinkedHashMap<>();
        formParams.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                body.put(key, values.getFirst());
            }
        });
        paymentService.handleVpiWebhook(vpiSignature, body);
        return ResponseEntity.ok("OK");
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
