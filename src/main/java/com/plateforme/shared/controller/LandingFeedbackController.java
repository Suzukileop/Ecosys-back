package com.plateforme.shared.controller;

import com.plateforme.shared.dto.LandingFeedbackRequest;
import com.plateforme.shared.service.LandingFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class LandingFeedbackController {

    private final LandingFeedbackService landingFeedbackService;

    @Operation(summary = "Send landing-page feedback email")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Feedback accepted and emailed"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/feedback")
    public ResponseEntity<Void> sendFeedback(
            @Valid @RequestBody LandingFeedbackRequest body,
            HttpServletRequest request) {
        landingFeedbackService.sendFeedback(body, resolveClientIp(request));
        return ResponseEntity.noContent().build();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
