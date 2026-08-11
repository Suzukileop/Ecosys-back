package com.plateforme.shared.service;

import com.plateforme.shared.entity.PlatformConfig;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.repository.PlatformConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformConfigService {

    public static final String TARIF_KEY = "TARIF_UNITAIRE_CENTS";
    private static final String CACHE_KEY = "platform_config:TARIF_UNITAIRE_CENTS";

    private final PlatformConfigRepository platformConfigRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public int getTarifUnitaireCents() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null && !cached.isBlank()) {
            try {
                return Integer.parseInt(cached.trim());
            } catch (NumberFormatException e) {
                log.warn("Cache tarif invalide, lecture DB");
            }
        }

        PlatformConfig row = platformConfigRepository.findById(TARIF_KEY)
                .orElseThrow(() -> new BusinessException("PLATFORM_CONFIG_MISSING",
                        "Configuration tarifaire introuvable"));

        int cents = Integer.parseInt(row.getConfigValue().trim());
        redisTemplate.opsForValue().set(CACHE_KEY, String.valueOf(cents), Duration.ofHours(1));
        return cents;
    }

    public int calculateMonthlyAmount(int nbPostsPerWeek) {
        return nbPostsPerWeek * 4 * getTarifUnitaireCents();
    }

    @Transactional
    public void updateTarifUnitaireCents(int cents) {
        if (cents <= 0 || cents > 1_000_000) {
            throw new BusinessException("INVALID_TARIF", "Tarif unitaire invalide");
        }
        PlatformConfig row = platformConfigRepository.findById(TARIF_KEY).orElseGet(() -> {
            PlatformConfig p = new PlatformConfig();
            p.setConfigKey(TARIF_KEY);
            return p;
        });
        row.setConfigValue(String.valueOf(cents));
        platformConfigRepository.save(row);
        redisTemplate.delete(CACHE_KEY);
        log.info("Tarif unitaire mis à jour : {} centimes", cents);
    }

    public void invalidateTarifCache() {
        redisTemplate.delete(CACHE_KEY);
    }
}
