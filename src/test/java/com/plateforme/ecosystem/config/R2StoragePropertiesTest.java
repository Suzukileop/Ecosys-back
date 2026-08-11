package com.plateforme.ecosystem.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R2StoragePropertiesTest {

    @Test
    void resolvedS3Endpoint_stripsTrailingBucketPath() {
        var p = new R2StorageProperties(
                true,
                "plateforme-media",
                "https://061fe162033d609fbd236b9444e487de.r2.cloudflarestorage.com/plateforme-media",
                "ak",
                "sk",
                "https://pub-test.r2.dev"
        );
        assertThat(p.resolvedS3Endpoint())
                .isEqualTo("https://061fe162033d609fbd236b9444e487de.r2.cloudflarestorage.com");
    }

    @Test
    void resolvedS3Endpoint_keepsBaseWhenNoSuffix() {
        var p = new R2StorageProperties(
                true,
                "plateforme-media",
                "https://061fe162033d609fbd236b9444e487de.r2.cloudflarestorage.com",
                "ak",
                "sk",
                "https://pub-test.r2.dev"
        );
        assertThat(p.resolvedS3Endpoint())
                .isEqualTo("https://061fe162033d609fbd236b9444e487de.r2.cloudflarestorage.com");
    }
}
