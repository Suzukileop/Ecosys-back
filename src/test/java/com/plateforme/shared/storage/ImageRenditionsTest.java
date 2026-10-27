package com.plateforme.shared.storage;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ImageRenditionsTest {
    private byte[] image(String format, int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (alpha) image.setRGB(0, 0, 0x80ff0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        image.flush();
        return out.toByteArray();
    }

    @Test void keepsLargeJpegSourceByteForByteAndBuildsSmallerCopies() throws Exception {
        byte[] original = image("jpeg", 3000, 1500, false);
        var result = ImageRenditions.build(original, "image/jpeg").orElseThrow();
        assertArrayEquals(original, result.canonicalBytes());
        assertEquals("image/jpeg", result.canonicalContentType());
        assertEquals(3000, ImageIO.read(new ByteArrayInputStream(result.canonicalBytes())).getWidth());
        assertFalse(result.derivatives().isEmpty());
        for (var copy : result.derivatives()) {
            assertTrue(copy.width() < 3000);
            var decoded = ImageIO.read(new ByteArrayInputStream(copy.bytes()));
            assertEquals(copy.width(), decoded.getWidth());
            assertEquals(copy.width() / 2, decoded.getHeight());
        }
    }

    @Test void keepsPngAndTransparencyWithoutChangingItsKey() throws Exception {
        byte[] original = image("png", 2400, 1200, true);
        var result = ImageRenditions.build(original, "image/png").orElseThrow();
        assertArrayEquals(original, result.canonicalBytes());
        assertEquals("image/png", result.canonicalContentType());
        assertEquals("portrait.png", ImageRenditions.canonicalKey("portrait.png", "image/png", result.canonicalContentType()));
        for (var copy : result.derivatives()) {
            assertEquals("image/png", copy.contentType());
            assertTrue(ImageIO.read(new ByteArrayInputStream(copy.bytes())).getColorModel().hasAlpha());
        }
    }

    @Test void neverUpscalesSmallSources() throws Exception {
        byte[] original = image("jpeg", 160, 80, false);
        var result = ImageRenditions.build(original, "image/jpeg").orElseThrow();
        assertArrayEquals(original, result.canonicalBytes());
        assertTrue(result.derivatives().isEmpty());
        assertEquals(160, ImageRenditions.buildOne(original, "image/jpeg", 640).orElseThrow().width());
    }

    @Test void onDemandCopiesKeepAspectRatioAndInvalidInputIsIgnored() throws Exception {
        byte[] original = image("jpeg", 1600, 800, false);
        var copy = ImageRenditions.buildOne(original, "image/jpeg", 640).orElseThrow();
        var decoded = ImageIO.read(new ByteArrayInputStream(copy.bytes()));
        assertEquals(640, decoded.getWidth());
        assertEquals(320, decoded.getHeight());
        assertTrue(ImageRenditions.build(new byte[]{1, 2, 3}, "image/jpeg").isEmpty());
    }
}
