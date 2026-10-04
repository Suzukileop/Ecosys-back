package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.storage.ImageRenditions;
import com.plateforme.ecosystem.storage.StorageSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Sert les fichiers écrits par {@link com.plateforme.ecosystem.storage.LocalDevStorageService}
 * sous {@code app.storage.local-dir}. Les URLs stockées en base sont du type
 * {@code {publicBaseUrl}/api/storage/demos/...}} pour être utilisables dans le navigateur.
 *
 * <p>A {@code ?w=} query asks for the stored derivative at that width instead of the canonical
 * file — the same contract an image CDN exposes. Uploads write their derivatives up front
 * ({@link ImageRenditions}); anything older, or any width that went missing, is built on first
 * request and written beside the canonical file so it is only ever paid once.
 */
@RestController
@RequestMapping("/api/storage")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Lecture des fichiers stockés en mode local (démo / refs)")
public class StorageController {

    private static final String PREFIX = "/api/storage/";

    private final StorageSignature storageSignature;

    /** Keeps a hostile {@code ?w=} from turning a read into a full-size decode. */
    private static final int MAX_REQUESTED_WIDTH = 4096;

    @Value("${app.storage.local-dir:./build/storage-uploads}")
    private String localDir;

    @Operation(summary = "Récupérer un fichier stocké côté serveur (mode local)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier retourné"),
            @ApiResponse(responseCode = "404", description = "Fichier introuvable")
    })
    @GetMapping("/**")
    public ResponseEntity<Resource> getLocalFile(
            HttpServletRequest request,
            @Parameter(description = "Largeur d'affichage souhaitée en pixels ; sert le rendu stocké le plus proche")
            @RequestParam(name = "w", required = false) Integer requestedWidth)
            throws IOException {
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

        /*
         * This route is unauthenticated by necessity — an <img> or <video> never carries the
         * bearer token — so a private object is gated on the signature issued with its URL
         * instead. 404 rather than 403: a wrong signature must not confirm that the key exists.
         */
        if (StorageSignature.isPrivateKey(objectKey)
                && !storageSignature.isValid(
                        objectKey,
                        request.getParameter(StorageSignature.EXPIRY_PARAM),
                        request.getParameter(StorageSignature.SIGNATURE_PARAM))) {
            log.debug("Unsigned or expired request for private key={}", objectKey);
            return ResponseEntity.notFound().build();
        }

        Path base = Path.of(localDir).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            log.warn("Tentative d'accès fichier hors dossier localDir base={} target={}", base, target);
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        Path served = resolveWidth(base, objectKey, target, requestedWidth);
        String contentType = probeContentType(served);
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        FileSystemResource resource = new FileSystemResource(served);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        String disposition = request.getParameter("disposition");
        String filename = request.getParameter("filename");
        if ("attachment".equalsIgnoreCase(disposition) && filename != null && !filename.isBlank()) {
            String safe = filename.replace("\"", "").replace("\r", "").replace("\n", "").trim();
            response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe + "\"");
        }
        return response.body(resource);
    }

    /**
     * Picks the stored derivative for {@code requestedWidth}, building it if it is missing.
     * Falls back to the canonical file whenever a width is not applicable — an unknown parameter
     * must never turn into a 404 on media that exists.
     */
    private Path resolveWidth(Path base, String objectKey, Path canonical, Integer requestedWidth) {
        if (requestedWidth == null || requestedWidth <= 0 || requestedWidth > MAX_REQUESTED_WIDTH) {
            return canonical;
        }
        /* A derivative asking for a derivative would recurse through the suffix forever. */
        if (objectKey.contains("__w")) {
            return canonical;
        }
        int rung = snapToRung(requestedWidth);
        if (rung <= 0) {
            return canonical;
        }
        for (String type : new String[] {"image/jpeg", "image/png"}) {
            Path stored = base.resolve(ImageRenditions.derivativeKey(objectKey, rung, type)).normalize();
            if (stored.startsWith(base) && Files.isRegularFile(stored)) {
                return stored;
            }
        }
        return buildDerivative(base, objectKey, canonical, rung);
    }

    /** Smallest stored rung that still covers the request; 0 when the canonical file is the answer. */
    private static int snapToRung(int requestedWidth) {
        for (int rung : ImageRenditions.DERIVATIVE_WIDTHS) {
            if (rung >= requestedWidth) {
                return rung;
            }
        }
        return 0;
    }

    /**
     * A derivative has to be meaningfully lighter than the canonical file to be worth a second
     * copy on disk. Above this ratio the canonical file is served instead.
     */
    private static final double WORTHWHILE_RATIO = 0.9;

    /**
     * Builds and stores one missing width. On any failure the canonical file is returned: a slow
     * response is always better than a broken image.
     */
    private Path buildDerivative(Path base, String objectKey, Path canonical, int rung) {
        String contentType = probeContentType(canonical);
        if (!ImageRenditions.isSupported(contentType)) {
            return canonical;
        }
        try {
            byte[] original = Files.readAllBytes(canonical);
            var rendition = ImageRenditions.buildOne(original, contentType, rung);
            if (rendition.isEmpty()) {
                return canonical;
            }
            var built = rendition.get();
            /*
             * The top rung often matches a canonical file that is already the right size — an
             * upload processed at ingest, say. Re-encoding it would only duplicate it. A legacy
             * PNG of the same width is the opposite case: no downscale, but flattening to JPEG
             * still cuts it by an order of magnitude. Comparing the bytes tells the two apart
             * without guessing from dimensions.
             */
            if (built.bytes().length > original.length * WORTHWHILE_RATIO) {
                return canonical;
            }
            Path stored = base.resolve(ImageRenditions.derivativeKey(objectKey, rung, built.contentType())).normalize();
            if (!stored.startsWith(base)) {
                return canonical;
            }
            Files.createDirectories(stored.getParent());
            /* Write-then-move so a concurrent reader never sees a half-written derivative. */
            Path temp = Files.createTempFile(stored.getParent(), ".rendition-", ".tmp");
            try {
                Files.write(temp, built.bytes());
                Files.move(temp, stored, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            log.info("Derivative built on demand key={} w={} {}KB", objectKey, rung, built.bytes().length / 1024);
            return stored;
        } catch (IOException | RuntimeException ex) {
            log.warn("Derivative build failed key={} w={}: {}", objectKey, rung, ex.toString());
            return canonical;
        }
    }

    /**
     * Content type from the bytes, not the file name: a PNG upload flattened to JPEG keeps its
     * original extension on files stored before {@link ImageRenditions#canonicalKey} existed.
     */
    private static String probeContentType(Path path) {
        try (var in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(12);
            if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                return "image/png";
            }
            if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return "image/webp";
            }
        } catch (IOException ex) {
            log.debug("Unable to sniff {}: {}", path, ex.toString());
        }
        try {
            String probed = Files.probeContentType(path);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ex) {
            log.debug("probeContentType failed {}: {}", path, ex.toString());
        }
        return fallbackByExtension(path);
    }

    private static String fallbackByExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return null;
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
