package com.plateforme.ecosystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.ecosystem.config.DeepSeekProperties;
import com.plateforme.ecosystem.entity.ChatMessage;
import com.plateforme.ecosystem.entity.ChatSenderType;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.ChatMessageRepository;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.shared.exception.ServiceUnavailableException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepSeekBotServiceTest {

    @Mock
    private NicheRequestRepository nicheRequestRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EcosystemService ecosystemService;

    private DeepSeekProperties props;

    private DeepSeekBotService deepSeekBotService;

    private UUID clientId;
    private UUID requestId;
    private User client;
    private NicheRequest niche;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        client = new User();
        client.setId(clientId);
        props = new DeepSeekProperties("http://localhost:9/fake", "x", "deepseek-chat", 500, 0.7, false);
        deepSeekBotService = new DeepSeekBotService(
                nicheRequestRepository,
                chatMessageRepository,
                userRepository,
                props,
                WebClient.builder().build(),
                new ObjectMapper(),
                ecosystemService
        );

        niche = new NicheRequest();
        niche.setId(requestId);
        niche.setClient(client);
        niche.setStatus(NicheStatus.PENDING);
        niche.setNicheTheme("Fitness");
        niche.setDescription("Desc");
        niche.setLanguage("FR");
        niche.setPlatforms(java.util.List.of("INSTAGRAM"));
        niche.setNbPostsPerWeek((short) 3);
    }

    @Test
    @DisplayName("START_CONVERSATION : pas d'appel DeepSeek, réponse locale")
    void sendBotMessage_startConversation_localWelcome() {
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(niche));
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(anyString())).thenReturn(List.of());
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-A", "T", "D", "FR", 3, java.util.List.of("INSTAGRAM"),
                        null, null, null, null, 1000, "10 €", "PENDING", "UNPAID", false,
                        null, null, null, null, null, "BOT_CHAT", null, null, null, "e", "n"
                ));

        var res = deepSeekBotService.sendBotMessage(requestId, clientId, "START_CONVERSATION");

        assertThat(res.botMessage()).isNotBlank();
        assertThat(res.botConfirmed()).isFalse();
        assertThat(res.readyToConfirm()).isFalse();
        verify(chatMessageRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("START_CONVERSATION idempotent : historique existant → pas de nouveau save")
    void sendBotMessage_startConversation_skipsSaveWhenHistoryExists() {
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(niche));
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        ChatMessage bot = new ChatMessage();
        bot.setSenderType(ChatSenderType.BOT);
        bot.setContent("Bonjour déjà enregistré");
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc("niche-" + requestId)).thenReturn(List.of(bot));
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-A", "T", "D", "FR", 3, java.util.List.of("INSTAGRAM"),
                        null, null, null, null, 1000, "10 €", "PENDING", "UNPAID", false,
                        null, null, null, null, null, "BOT_CHAT", null, null, null, "e", "n"
                ));

        var res = deepSeekBotService.sendBotMessage(requestId, clientId, "START_CONVERSATION");

        assertThat(res.botMessage()).contains("Bonjour");
        assertThat(res.botConfirmed()).isFalse();
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Clé API absente → 503")
    void sendBotMessage_noApiKey_throws503() {
        props = new DeepSeekProperties("http://localhost:9/fake", "", "deepseek-chat", 500, 0.7, false);
        deepSeekBotService = new DeepSeekBotService(
                nicheRequestRepository,
                chatMessageRepository,
                userRepository,
                props,
                WebClient.builder().build(),
                new ObjectMapper(),
                ecosystemService
        );

        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(niche));
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> deepSeekBotService.sendBotMessage(requestId, clientId, "hello"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    @DisplayName("stub-without-key : réponse locale sans appel réseau")
    void sendBotMessage_stubWithoutKey_returnsStubWithoutThrowing() {
        props = new DeepSeekProperties("http://localhost:9/fake", "", "deepseek-chat", 500, 0.7, true);
        deepSeekBotService = new DeepSeekBotService(
                nicheRequestRepository,
                chatMessageRepository,
                userRepository,
                props,
                WebClient.builder().build(),
                new ObjectMapper(),
                ecosystemService
        );

        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(niche));
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        ChatMessage human = new ChatMessage();
        human.setSenderType(ChatSenderType.HUMAN);
        human.setContent("bonjour");
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(anyString())).thenReturn(List.of(human));
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-A", "T", "D", "FR", 3, java.util.List.of("INSTAGRAM"),
                        null, null, null, null, 1000, "10 €", "PENDING", "UNPAID", false,
                        null, null, null, null, null, "BOT_CHAT", null, null, null, "e", "n"
                ));

        var res = deepSeekBotService.sendBotMessage(requestId, clientId, "bonjour");

        assertThat(res.botMessage()).contains("Mode secours");
        verify(chatMessageRepository, atLeastOnce()).save(any());
    }
}
