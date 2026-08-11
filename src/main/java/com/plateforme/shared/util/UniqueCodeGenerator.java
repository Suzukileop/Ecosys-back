package com.plateforme.shared.util;

import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class UniqueCodeGenerator {

    private final NicheRequestRepository nicheRequestRepository;

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_RETRIES = 10;
    private static final int CODE_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String code = "MCT-" + generateSuffix();
            if (!nicheRequestRepository.existsByUniqueCode(code)) {
                log.debug("Code unique généré en {} tentative(s) : {}", attempt, code);
                return code;
            }
            log.warn("Collision détectée pour le code {} (tentative {})", code, attempt);
        }
        throw new BusinessException("UNIQUE_CODE_EXHAUSTED",
                "Impossible de générer un code unique après " + MAX_RETRIES + " tentatives");
    }

    private String generateSuffix() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
