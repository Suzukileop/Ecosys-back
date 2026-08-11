package com.plateforme.ecosystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "app.r2", name = "enabled", havingValue = "true")
@Slf4j
public class R2ClientConfiguration {

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(R2StorageProperties props) {
        String endpoint = props.resolvedS3Endpoint();
        if (endpoint.isEmpty() || props.bucket().isEmpty()) {
            throw new IllegalStateException(
                    "Avec app.r2.enabled=true, renseignez app.r2.endpoint (ex. https://<account>.r2.cloudflarestorage.com) "
                            + "et app.r2.bucket.");
        }
        if (props.accessKey().isEmpty() || props.secretKey().isEmpty()) {
            throw new IllegalStateException("Avec app.r2.enabled=true, renseignez app.r2.access-key et app.r2.secret-key.");
        }

        log.info("Client S3 R2 : endpoint={}, bucket={}", endpoint, props.bucket());

        return S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2S3Presigner(R2StorageProperties props) {
        String endpoint = props.resolvedS3Endpoint();
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
