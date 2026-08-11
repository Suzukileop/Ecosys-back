package com.plateforme.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthStateService {

    public static final String SIGNUP_STATE = "SIGNUP";

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration EXCHANGE_TTL = Duration.ofMinutes(5);
    private static final Duration EXCHANGE_REPLAY_TTL = Duration.ofMinutes(5);
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    public String createSignupState() {
        return createStateValue(SIGNUP_STATE);
    }

    public String createLoginState(String role) {
        return createStateValue(normalizeRole(role));
    }

    public Optional<String> consumeState(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        String key = stateKey(state);
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.delete(key);
            return Optional.of(value);
        }
        return Optional.empty();
    }

    public String storeExchangePayload(String payload) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(exchangeKey(code), payload, EXCHANGE_TTL);
        return code;
    }

    public Optional<String> consumeExchangePayload(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String key = exchangeKey(code);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload != null) {
            redisTemplate.delete(key);
            redisTemplate.opsForValue().set(exchangeReplayKey(code), payload, EXCHANGE_REPLAY_TTL);
            return Optional.of(payload);
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(exchangeReplayKey(code)));
    }

    public String storePendingProfile(String payload) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(pendingKey(code), payload, PENDING_TTL);
        return code;
    }

    public Optional<String> consumePendingProfile(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String key = pendingKey(code);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload != null) {
            redisTemplate.delete(key);
            return Optional.of(payload);
        }
        return Optional.empty();
    }

    public Optional<String> peekPendingProfile(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(pendingKey(code)));
    }

    private String createStateValue(String value) {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(stateKey(state), value, STATE_TTL);
        return state;
    }

    private String stateKey(String state) {
        return "oauth:state:" + state;
    }

    private String exchangeKey(String code) {
        return "oauth:exchange:" + code;
    }

    private String exchangeReplayKey(String code) {
        return "oauth:exchange:replay:" + code;
    }

    private String pendingKey(String code) {
        return "oauth:pending:" + code;
    }

    private String normalizeRole(String role) {
        return "CREATOR";
    }
}
