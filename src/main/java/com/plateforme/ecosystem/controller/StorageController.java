package com.plateforme.ecosystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sert les fichiers écrits par {@link com.plateforme.ecosystem.storage.LocalDevStorageService}
 * sous {@code app.storage.local-dir}. Les URLs stockées en base sont du type
 * {@code {publicBaseUrl}/api/storage/demos/...}} pour être utilisables dans le navigateur.
 */
@RestController
@RequestMapping("/api/storage")
@Slf4j
@Tag(name = "Storage", description = "Lecture des fichiers stockés en mode local (démo / refs)")
public class StorageController {

    private static final String PREFIX = "/api/storage/";

    @Value("${app.storage.local-dir:./build/storage-uploads}")
    private String localDir;

    @Operation(summary = "Récupérer un fichier stocké côté serveur (mode local)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier retourné"),
            @ApiResponse(responseCode = "404", description = "Fichier introuvable")
    })
    @GetMapping("/**")
    public ResponseEntity<Resource> getLocalFile(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        int q = uri.indexOf('?');
        if (q > 0) {
            uri = uri.substring(0, q);
        }
        if (!uri.startsWith(PREFIX)) {
            return ResponseEntity.notFound().build();
        }
        String objectKey = uri.substring(PREFIX.length());
        if (objectKey.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        objectKey = decodeObjectKey(objectKey);

        Path base = Path.of(localDir).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            log.warn("Tentative d'accès fichier hors dossier localDir base={} target={}", base, target);
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(target);
        MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;

        FileSystemResource resource = new FileSystemResource(target);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength());
        String disposition = request.getParameter("disposition");
        String filename = request.getParameter("filename");
        if ("attachment".equalsIgnoreCase(disposition) && filename != null && !filename.isBlank()) {
            String safe = filename.replace("\"", "").replace("\r", "").replace("\n", "").trim();
            response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe + "\"");
        }
        return response.body(resource);
    }

    /** Décode %20 et autres séquences — le fichier sur disque utilise le nom réel (espaces). */
    private static String decodeObjectKey(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }
}
