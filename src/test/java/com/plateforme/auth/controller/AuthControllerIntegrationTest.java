package com.plateforme.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.service.AuthService;
import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.user.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    private AuthResponse buildTestAuthResponse() {
        UserDto userDto = new UserDto(
                UUID.randomUUID(),
                "test@example.com",
                "Test User",
                "testuser",
                null,
                Set.of("ROLE_CREATOR"),
                LocalDateTime.now(),
                false
        );
        return new AuthResponse("access-token", "refresh-token", "Bearer", 900L, userDto);
    }

    @Test
    void signup_returns201_whenValidRequest() throws Exception {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "New User", "newuser", "CREATOR");
        when(authService.signup(any(SignupRequest.class))).thenReturn(buildTestAuthResponse());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void signup_returns400_whenInvalidEmail() throws Exception {
        SignupRequest request = new SignupRequest("not-an-email", "password123", "New User", "newuser", "CREATOR");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_returns400_whenPasswordTooShort() throws Exception {
        SignupRequest request = new SignupRequest("valid@example.com", "short", "New User", "newuser", "CREATOR");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200_whenValidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(authService.login(any(LoginRequest.class))).thenReturn(buildTestAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user").exists());
    }

    @Test
    void login_returns400_whenEmptyBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_returns200_whenValidRefreshToken() throws Exception {
        when(authService.refreshToken("valid-refresh-token")).thenReturn(buildTestAuthResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "CREATOR")
    void logout_returns204_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isUnauthorized());
    }
}
