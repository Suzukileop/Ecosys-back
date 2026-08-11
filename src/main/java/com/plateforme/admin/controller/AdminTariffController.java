package com.plateforme.admin.controller;

import com.plateforme.shared.service.PlatformConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin configuration", description = "Tarification plateforme")
@SecurityRequirement(name = "bearerAuth")
public class AdminTariffController {

    private final PlatformConfigService platformConfigService;

    @Operation(summary = "Lire le tarif unitaire (centimes / post)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarif courant")
    })
    @GetMapping("/tarif")
    public ResponseEntity<Map<String, Object>> getTarif() {
        int cents = platformConfigService.getTarifUnitaireCents();
        BigDecimal eur = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String formatted = NumberFormat.getNumberInstance(Locale.FRANCE).format(eur) + " €";
        return ResponseEntity.ok(Map.of(
                "tarifUnitaireCents", cents,
                "formatted", formatted
        ));
    }

    @Operation(summary = "Mettre à jour le tarif unitaire")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarif mis à jour")
    })
    @PutMapping("/tarif")
    public ResponseEntity<Map<String, Object>> updateTarif(@Valid @RequestBody TarifUpdate body) {
        platformConfigService.updateTarifUnitaireCents(body.tarifUnitaireCents());
        return getTarif();
    }

    public record TarifUpdate(
            @NotNull @Min(1) Integer tarifUnitaireCents
    ) {
    }
}
