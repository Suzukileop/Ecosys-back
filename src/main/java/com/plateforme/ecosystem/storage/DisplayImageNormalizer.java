package com.plateforme.ecosystem.storage;

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
import java.util.Iterator;
import java.util.Optional;

/**
 * Downscales oversized public uploads so Team/Gallery cards do not decode 20–30 MB originals.
 * Existing files already in R2 are unchanged; Next.js image optimization covers those on the public site.
 */
@Slf4j
public final class DisplayImageNormalizer {

    public static final int MAX_EDGE_PX = 1920;
    public static final float JPEG_QUALITY = 0.82f;

    public record NormalizedImage(byte[] bytes, String contentType) {}

    private DisplayImageNormalizer() {}

    public static Optional<NormalizedImage> maybeNormalize(byte[] original, String contentType) {
        if (original == null || original.length == 0 || contentType == null) {
            return Optional.empty();
        }
        String type = contentType.toLowerCase();
        if (!type.equals("image/jpeg") && !type.equals("image/jpg") && !type.equals("image/png")) {
            return Optional.empty();
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) return Optional.empty();
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) return Optional.empty();
            int longest = Math.max(width, height);
            if (longest <= MAX_EDGE_PX && original.length <= 1_200_000) {
                return Optional.empty();
            }
            double scale = longest > MAX_EDGE_PX ? (double) MAX_EDGE_PX / longest : 1.0;
            int nextW = Math.max(1, (int) Math.round(width * scale));
            int nextH = Math.max(1, (int) Math.round(height * scale));
            BufferedImage dest = new BufferedImage(nextW, nextH, jpegCompatibleType(source, type));
            Graphics2D graphics = dest.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, nextW, nextH, null);
            graphics.dispose();

            boolean jpeg = type.contains("jpeg") || type.contains("jpg") || !source.getColorModel().hasAlpha();
            byte[] encoded = jpeg ? encodeJpeg(dest) : encodePng(dest);
            if (encoded.length >= original.length) {
                return Optional.empty();
            }
            log.info(
                    "Normalized public image {}x{} {}KB -> {}x{} {}KB",
                    width,
                    height,
                    original.length / 1024,
                    nextW,
                    nextH,
                    encoded.length / 1024);
            return Optional.of(new NormalizedImage(encoded, jpeg ? "image/jpeg" : "image/png"));
        } catch (Exception ex) {
            log.warn("Skip image normalize: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static int jpegCompatibleType(BufferedImage source, String type) {
        if (type.contains("png") && source.getColorModel().hasAlpha()) {
            return BufferedImage.TYPE_INT_ARGB;
        }
        return BufferedImage.TYPE_INT_RGB;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG encode failed");
        }
        return out.toByteArray();
    }
}
