package com.plateforme.shared.storage;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds the stored renditions of an uploaded image: one canonical file plus fixed-width
 * derivatives, the way an image CDN does it at ingest rather than on every read.
 *
 * <p>Why derivatives are stored at all, given the frontend already asks Next for an optimized
 * image: the optimizer has to read the <em>upstream</em> first. With only a full-size original on
 * disk that is a multi-megabyte read and decode for every new width, every cache eviction, and
 * every private file the optimizer cannot touch at all. Handing it a ~60 KB derivative instead
 * makes that cost disappear. See {@link StorageController} for how a width is resolved.
 *
 * <p>Derivatives are JPEG (or PNG when the source has real transparency) on purpose: ImageIO
 * writes both without a native library, and the format only has to survive one more hop — the
 * browser receives AVIF/WebP from the optimizer regardless. Trading ~25% on an intermediate
 * artifact for zero native dependencies on every platform the backend runs on is worth it.
 */
@Slf4j
public final class ImageRenditions {

    /**
     * Widths kept on disk. Mirrors the rungs the frontend asks for (see media-image-url.ts).
     * The top rung matches {@link #CANONICAL_MAX_EDGE} so even a lightbox at full zoom is served a
     * derivative rather than whatever the canonical file happens to be — which, for uploads that
     * predate this pipeline, is still the untouched original.
     */
    public static final int[] DERIVATIVE_WIDTHS = {256, 640, 1280, 2048};

    /** No display in the product is wider than this, so the canonical file is capped here. */
    public static final int CANONICAL_MAX_EDGE = 2048;

    private static final float JPEG_QUALITY = 0.82f;
    private static final float DERIVATIVE_QUALITY = 0.78f;

    /** Beyond this an upload is almost certainly a photo, not a crisp UI asset worth keeping lossless. */
    private static final int PNG_RECOMPRESS_THRESHOLD_BYTES = 400_000;

    public record Rendition(int width, byte[] bytes, String contentType) {}

    public record RenditionSet(
            int sourceWidth,
            int sourceHeight,
            byte[] canonicalBytes,
            String canonicalContentType,
            List<Rendition> derivatives) {}

    private ImageRenditions() {}

    public static boolean isSupported(String contentType) {
        if (contentType == null) return false;
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("image/png");
    }

    /**
     * The key the canonical file is stored under. Flattening a PNG photo to JPEG changes the
     * format, so the extension has to follow — otherwise every reader that infers a content type
     * from the file name (including {@code Files.probeContentType}) reports the wrong one.
     * Callers use the returned URL/key, so the rename stays internal to the upload.
     */
    public static String canonicalKey(String objectKey, String sourceContentType, String canonicalContentType) {
        if (objectKey == null || canonicalContentType == null
                || canonicalContentType.equalsIgnoreCase(sourceContentType)) {
            return objectKey;
        }
        String extension = extensionFor(canonicalContentType);
        int dot = objectKey.lastIndexOf('.');
        int slash = objectKey.lastIndexOf('/');
        if (dot > slash && dot > 0) {
            return objectKey.substring(0, dot) + extension;
        }
        return objectKey + extension;
    }

    /** Object key of the derivative at {@code width} for {@code objectKey}. */
    public static String derivativeKey(String objectKey, int width, String contentType) {
        return objectKey + "__w" + width + extensionFor(contentType);
    }

    private static String extensionFor(String contentType) {
        return "image/png".equalsIgnoreCase(contentType) ? ".png" : ".jpg";
    }

    /**
     * Decodes once, then builds the canonical file and every derivative narrower than the source.
     * Empty when the payload is not a still image this can re-encode — the caller then stores the
     * upload untouched.
     */
    public static Optional<RenditionSet> build(byte[] original, String contentType) {
        if (original == null || original.length == 0 || !isSupported(contentType)) {
            return Optional.empty();
        }
        boolean cacheWasOn = ImageIO.getUseCache();
        ImageIO.setUseCache(false);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                return Optional.empty();
            }
            int width = source.getWidth();
            int height = source.getHeight();
            boolean alpha = hasMeaningfulAlpha(source, contentType);

            byte[] canonicalBytes;
            String canonicalType;
            int longest = Math.max(width, height);
            boolean oversized = longest > CANONICAL_MAX_EDGE;
            boolean heavyPng = !alpha && original.length > PNG_RECOMPRESS_THRESHOLD_BYTES;

            if (!oversized && !heavyPng && original.length <= 1_200_000) {
                /* Already small and well-encoded — re-encoding would only lose detail. */
                canonicalBytes = original;
                canonicalType = contentType;
            } else {
                BufferedImage canonical = oversized
                        ? scaleToLongestEdge(source, CANONICAL_MAX_EDGE, alpha)
                        : source;
                canonicalType = alpha ? "image/png" : "image/jpeg";
                byte[] encoded = encode(canonical, canonicalType, JPEG_QUALITY);
                /* A re-encode that grew means the original was already better; keep it. */
                boolean keepOriginal = encoded.length >= original.length && !oversized;
                canonicalBytes = keepOriginal ? original : encoded;
                if (keepOriginal) {
                    canonicalType = contentType;
                }
                if (canonical != source) {
                    canonical.flush();
                }
            }

            String derivativeType = alpha ? "image/png" : "image/jpeg";
            List<Rendition> derivatives = new ArrayList<>(DERIVATIVE_WIDTHS.length);
            BufferedImage step = source;
            for (int i = DERIVATIVE_WIDTHS.length - 1; i >= 0; i--) {
                int target = DERIVATIVE_WIDTHS[i];
                /* Upscaling a narrow source wastes bytes and adds nothing the browser can use. */
                if (target >= width) {
                    continue;
                }
                BufferedImage scaled = scaleToWidth(step, target, alpha);
                derivatives.add(new Rendition(target, encode(scaled, derivativeType, DERIVATIVE_QUALITY), derivativeType));
                if (step != source) {
                    step.flush();
                }
                /* Downscale the next rung from this one: cheaper and smoother than from the source. */
                step = scaled;
            }
            if (step != source) {
                step.flush();
            }
            source.flush();

            log.info(
                    "Renditions {}x{} source={}KB canonical={}KB derivatives={}",
                    width,
                    height,
                    original.length / 1024,
                    canonicalBytes.length / 1024,
                    derivatives.stream().map(d -> d.width() + ":" + d.bytes().length / 1024 + "KB").toList());

            return Optional.of(new RenditionSet(width, height, canonicalBytes, canonicalType, derivatives));
        } catch (Exception ex) {
            log.warn("Skip renditions: {}", ex.toString());
            return Optional.empty();
        } finally {
            ImageIO.setUseCache(cacheWasOn);
        }
    }

    /**
     * Builds a single derivative from an already-stored image — the on-demand path for files that
     * predate this pipeline. See {@link StorageController}.
     */
    public static Optional<Rendition> buildOne(byte[] original, String contentType, int width) {
        if (original == null || original.length == 0 || !isSupported(contentType) || width <= 0) {
            return Optional.empty();
        }
        boolean cacheWasOn = ImageIO.getUseCache();
        ImageIO.setUseCache(false);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null || source.getWidth() <= 0) {
                return Optional.empty();
            }
            boolean alpha = hasMeaningfulAlpha(source, contentType);
            String type = alpha ? "image/png" : "image/jpeg";
            int target = Math.min(width, source.getWidth());
            BufferedImage scaled = target == source.getWidth() ? source : scaleToWidth(source, target, alpha);
            byte[] bytes = encode(scaled, type, DERIVATIVE_QUALITY);
            if (scaled != source) {
                scaled.flush();
            }
            source.flush();
            return Optional.of(new Rendition(target, bytes, type));
        } catch (Exception ex) {
            log.warn("Skip on-demand rendition w={}: {}", width, ex.toString());
            return Optional.empty();
        } finally {
            ImageIO.setUseCache(cacheWasOn);
        }
    }

    /**
     * A PNG whose alpha channel is fully opaque is a photo someone exported as PNG — by far the
     * most common oversized upload here. Flattening it to JPEG is where the big savings come from,
     * so the channel is sampled rather than trusted.
     */
    private static boolean hasMeaningfulAlpha(BufferedImage image, String contentType) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        if (!"image/png".equalsIgnoreCase(contentType)) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / 64);
        int stepY = Math.max(1, height / 64);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                if ((image.getRGB(x, y) >>> 24) < 250) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BufferedImage scaleToLongestEdge(BufferedImage source, int maxEdge, boolean alpha) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double scale = (double) maxEdge / longest;
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return scaleTo(source, w, h, alpha);
    }

    private static BufferedImage scaleToWidth(BufferedImage source, int targetWidth, boolean alpha) {
        int h = Math.max(1, Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));
        return scaleTo(source, targetWidth, h, alpha);
    }

    private static BufferedImage scaleTo(BufferedImage source, int w, int h, boolean alpha) {
        BufferedImage dest = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, w, h, null);
        graphics.dispose();
        return dest;
    }

    /**
     * JPEG has no alpha channel, and ImageIO does not drop one for you: handing its writer an
     * image whose colour model carries alpha fails with "Bogus input colorspace", even when every
     * pixel is opaque. Most oversized PNG uploads decode to exactly such a buffer, so flatten onto
     * white first. (This is the failure that silently left 5–6 MB avatars unshrunk before.)
     */
    private static BufferedImage flattenForJpeg(BufferedImage image) {
        if (!image.getColorModel().hasAlpha() && image.getType() != BufferedImage.TYPE_CUSTOM) {
            return image;
        }
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = opaque.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return opaque;
    }

    private static byte[] encode(BufferedImage image, String contentType, float quality) throws IOException {
        if ("image/png".equalsIgnoreCase(contentType)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("PNG encode failed");
            }
            return out.toByteArray();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer");
        }
        BufferedImage source = flattenForJpeg(image);
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(source, null, null), param);
        } finally {
            writer.dispose();
            if (source != image) {
                source.flush();
            }
        }
        return out.toByteArray();
    }
}
