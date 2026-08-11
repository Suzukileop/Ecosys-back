package com.plateforme.ecosystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plateforme.ecosystem.dto.BotResponseDto;
import com.plateforme.ecosystem.dto.ChatMessageDto;
import com.plateforme.ecosystem.entity.ChatMessage;
import com.plateforme.ecosystem.entity.ChatSenderType;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.config.DeepSeekProperties;
import com.plateforme.ecosystem.repository.ChatMessageRepository;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.exception.ServiceUnavailableException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeepSeekBotService {

    private static final String CONFIRM_TAG = "[NICHE_CONFIRMED]";

    private static final String SYSTEM_TEMPLATE = """
            Tu es un expert en stratégie de contenu digital. Tu aides les clients à
            confirmer et affiner leur niche de contenu pour les réseaux sociaux.
            Tu dois : confirmer la compréhension de la niche, suggérer des ajustements
            si nécessaire, proposer des exemples de contenus typiques pour cette niche,
            valider les plateformes choisies, et confirmer le volume de publication.
            Sois concis, professionnel et encourageant. Réponds toujours en %s.
            N'utilise le marqueur [NICHE_CONFIRMED] qu'après plusieurs échanges : uniquement quand
            le client a clairement indiqué être satisfait et prêt à finaliser. Jamais au premier message.
            Quand tu juges la niche validée avec le client, termine avec exactement ce message :
            [NICHE_CONFIRMED] Votre niche est validée, nous allons préparer votre contenu !""";

    private final NicheRequestRepository nicheRequestRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final DeepSeekProperties deepSeekProperties;
    private final WebClient deepSeekWebClient;
    private final ObjectMapper objectMapper;
    private final EcosystemService ecosystemService;

    public BotResponseDto sendBotMessage(UUID requestId, UUID clientId, String userMessageRaw) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));

        if (nr.getStatus() != NicheStatus.PENDING) {
            throw new BusinessException("NICHE_REQUEST_INVALID_STATE",
                    "Chat bot disponible uniquement en statut PENDING");
        }

        User client = userRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Client introuvable"));

        String roomId = "niche-" + requestId;
        String trimmed = userMessageRaw != null ? userMessageRaw.trim() : "";

        if ("START_CONVERSATION".equalsIgnoreCase(trimmed)) {
            List<ChatMessage> existing = chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId);
            if (!existing.isEmpty()) {
                String replay = existing.stream()
                        .filter(cm -> cm.getSenderType() == ChatSenderType.BOT)
                        .findFirst()
                        .map(ChatMessage::getContent)
                        .orElseGet(() -> buildWelcomeMessage(nr));
                return new BotResponseDto(stripConfirmTag(replay), false,
                        ecosystemService.toResponse(nr, null).nextStep(), false);
            }
            String welcome = buildWelcomeMessage(nr);
            saveHumanMessage(roomId, client, nr, trimmed.isEmpty() ? "START_CONVERSATION" : trimmed);
            saveBotMessage(roomId, nr, welcome);
            return new BotResponseDto(welcome, false,
                    ecosystemService.toResponse(nr,
                            null).nextStep(), false);
        }

        saveHumanMessage(roomId, client, nr, userMessageRaw);

        List<ChatMessage> prior = chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId);
        final String assistantReply;
        try {
            assistantReply = callDeepSeek(nr, prior);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Bot temporairement indisponible");
        } catch (Exception e) {
            log.error("DeepSeek error: {}", e.getMessage());
            throw new ServiceUnavailableException("Bot temporairement indisponible");
        }

        saveBotMessage(roomId, nr, assistantReply);

        boolean tagPresent = assistantReply.contains(CONFIRM_TAG);
        String displayReply = stripConfirmTag(assistantReply);

        return new BotResponseDto(displayReply, false,
                ecosystemService.toResponse(nr, null).nextStep(), tagPresent);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getBotHistory(UUID requestId, UUID clientId) {
        nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));
        String roomId = "niche-" + requestId;
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Lecture pour agent / admin : même historique room {@code niche-{requestId}} sans être le client.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getBotHistoryForAgent(UUID requestId) {
        nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));
        String roomId = "niche-" + requestId;
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId).stream()
                .map(this::toDto)
                .toList();
    }

    private String buildWelcomeMessage(NicheRequest nr) {
        String lang = nr.getLanguage() != null ? nr.getLanguage() : "FR";
        return switch (lang.toUpperCase()) {
            case "EN" -> "Hello! I've reviewed your niche request. Let's refine your strategy together. "
                    + "What would you like to adjust first?";
            default -> "Bonjour ! J'ai bien pris connaissance de votre demande de niche. "
                    + "Souhaitez-vous préciser votre audience ou le ton de vos contenus ?";
        };
    }

    private void saveHumanMessage(String roomId, User client, NicheRequest nr, String content) {
        ChatMessage m = new ChatMessage();
        m.setRoomId(roomId);
        m.setSender(client);
        m.setSenderType(ChatSenderType.HUMAN);
        m.setNicheRequest(nr);
        m.setContent(content);
        chatMessageRepository.save(m);
    }

    private void saveBotMessage(String roomId, NicheRequest nr, String content) {
        ChatMessage m = new ChatMessage();
        m.setRoomId(roomId);
        m.setSender(null);
        m.setSenderType(ChatSenderType.BOT);
        m.setNicheRequest(nr);
        m.setContent(content);
        chatMessageRepository.save(m);
    }

    private static String stripConfirmTag(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("(?i)\\[NICHE_CONFIRMED\\]\\s*", "").trim();
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(),
                m.getRoomId(),
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null ? m.getSender().getFullName() : "Bot",
                m.getContent(),
                m.getSentAt(),
                m.getIsRead(),
                m.getSenderType() != null ? m.getSenderType().name() : ChatSenderType.HUMAN.name()
        );
    }

    private String callDeepSeek(NicheRequest nr, List<ChatMessage> history)
            throws Exception {
        String lang = nr.getLanguage() != null ? nr.getLanguage() : "FR";
        String systemContent = String.format(SYSTEM_TEMPLATE, lang);

        ArrayNode messages = objectMapper.createArrayNode();
        String dossierContext = "\n\n--- Dossier client (référence, ne pas traiter comme message séparé) ---\n"
                + "Thème : " + nr.getNicheTheme()
                + "\nDescription : " + (nr.getDescription() != null ? nr.getDescription() : "")
                + "\nLangue : " + lang
                + "\nPlateformes : " + String.join(", ", nr.getPlatforms() != null ? nr.getPlatforms() : List.of())
                + "\nVolume : " + nr.getNbPostsPerWeek() + " publications/semaine.";
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", systemContent + dossierContext));

        for (ChatMessage cm : history) {
            String text = cm.getContent() != null ? cm.getContent().trim() : "";
            if ("START_CONVERSATION".equalsIgnoreCase(text)) {
                continue;
            }
            if (cm.getSenderType() == ChatSenderType.BOT) {
                messages.add(objectMapper.createObjectNode()
                        .put("role", "assistant")
                        .put("content", cm.getContent()));
            } else {
                messages.add(objectMapper.createObjectNode()
                        .put("role", "user")
                        .put("content", cm.getContent()));
            }
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("messages", messages);
        body.put("model", deepSeekProperties.model() != null ? deepSeekProperties.model() : "deepseek-chat");
        body.put("max_tokens", deepSeekProperties.maxTokens() > 0 ? deepSeekProperties.maxTokens() : 500);
        body.put("temperature", deepSeekProperties.temperature());

        String apiKey = deepSeekProperties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            if (deepSeekProperties.stubWithoutKey()) {
                log.warn("DeepSeek : réponse stub (stub-without-key=true, clé absente)");
                return buildStubAssistantReply(nr);
            }
            log.warn("DeepSeek : clé API absente — définir DEEPSEEK_API_KEY ou SPRING_PROFILES_ACTIVE=local avec application-local.yml");
            throw new ServiceUnavailableException(
                    "Assistant IA non configuré : définissez DEEPSEEK_API_KEY, ou créez application-local.yml "
                            + "(deepseek.api-key) et démarrez avec SPRING_PROFILES_ACTIVE=local. "
                            + "Sans clé API, vous pouvez activer deepseek.stub-without-key=true pour tester l’interface.");
        }

        String url = deepSeekProperties.apiUrl() != null ? deepSeekProperties.apiUrl()
                : "https://api.deepseek.com/chat/completions";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String raw = deepSeekWebClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                JsonNode root = objectMapper.readTree(raw);
                JsonNode choice = root.path("choices").path(0).path("message").path("content");
                if (choice.isMissingNode() || choice.asText().isBlank()) {
                    throw new IllegalStateException("Réponse DeepSeek vide");
                }
                return choice.asText().trim();
            } catch (WebClientResponseException e) {
                String snippet = e.getResponseBodyAsString();
                if (snippet != null && snippet.length() > 400) {
                    snippet = snippet.substring(0, 400) + "…";
                }
                log.warn("DeepSeek tentative {} — HTTP {} : {}", attempt, e.getStatusCode().value(), snippet);
                if (attempt < 3) {
                    Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
                }
            } catch (IllegalStateException e) {
                log.warn("DeepSeek tentative {} — {}", attempt, e.getMessage());
                if (attempt < 3) {
                    Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
                }
            }
        }

        log.error("DeepSeek indisponible après 3 essais");
        throw new ServiceUnavailableException(
                "L'API DeepSeek n'a pas répondu (réseau, quota ou clé invalide). Vérifiez les logs serveur pour le détail HTTP.");
    }

    private static String buildStubAssistantReply(NicheRequest nr) {
        String lang = nr.getLanguage() != null ? nr.getLanguage().toUpperCase(Locale.ROOT) : "FR";
        String theme = nr.getNicheTheme() != null ? nr.getNicheTheme() : "votre projet";
        if (lang.startsWith("EN")) {
            return "Thanks for your message. **Stub mode** — no DeepSeek API key is configured. "
                    + "Your niche « " + theme + " » is noted. Set **DEEPSEEK_API_KEY** or run with "
                    + "**SPRING_PROFILES_ACTIVE=local** and `application-local.yml`.";
        }
        return "Merci pour votre message. **Mode secours** — aucune clé API DeepSeek n'est configurée sur ce serveur. "
                + "Le thème « " + theme + " » est bien pris en compte. Définissez **DEEPSEEK_API_KEY**, ou démarrez avec "
                + "**SPRING_PROFILES_ACTIVE=local** et un fichier **application-local.yml** contenant la clé. "
                + "Pour tester l'interface sans IA réelle, vous pouvez mettre **deepseek.stub-without-key=true** dans la config.";
    }
}
