package com.plateforme.ecosystem.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PublicMediaUrlResolverTest {

    private PublicMediaUrlResolver resolver;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        resolver = new PublicMediaUrlResolver();
        tempDir = Files.createTempDirectory("storage-resolver-test");
        ReflectionTestUtils.setField(resolver, "publicBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(resolver, "localDir", tempDir.toString());
        ReflectionTestUtils.setField(resolver, "r2Enabled", false);
    }

    @Test
    void resolvePublicUrl_rebuildsLegacyHostFromObjectKey() throws IOException {
        String objectKey = "profiles/public/user-1/avatar.webp";
        Path file = tempDir.resolve(objectKey);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "fake");

        String legacy = "http://127.0.0.1:9090/api/storage/" + objectKey;
        assertThat(resolver.resolvePublicUrl(legacy))
                .isEqualTo("http://localhost:8080/api/storage/" + objectKey);
    }

    @Test
    void resolvePublicUrl_returnsNullWhenLocalFileMissing() {
        assertThat(resolver.resolvePublicUrl("http://localhost:8080/api/storage/profiles/public/missing.webp"))
                .isNull();
    }

    @Test
    void resolvePublicUrl_passesThroughExternalAvatar() {
        String external = "https://lh3.googleusercontent.com/a/example";
        assertThat(resolver.resolvePublicUrl(external)).isEqualTo(external);
    }

    @Test
    void resolvePublicUrl_acceptsRawObjectKey() throws IOException {
        String objectKey = "profiles/public/user-2/photo.png";
        Path file = tempDir.resolve(objectKey);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "fake");

        assertThat(resolver.resolvePublicUrl(objectKey))
                .isEqualTo("http://localhost:8080/api/storage/" + objectKey);
    }
}
