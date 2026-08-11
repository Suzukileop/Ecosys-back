package com.plateforme;

import com.plateforme.ecosystem.config.DeepSeekProperties;
import com.plateforme.ecosystem.config.R2StorageProperties;
import com.plateforme.shared.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({
        DeepSeekProperties.class,
        R2StorageProperties.class,
        RateLimitProperties.class
})
public class NoProblemeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoProblemeApplication.class, args);
    }
}
