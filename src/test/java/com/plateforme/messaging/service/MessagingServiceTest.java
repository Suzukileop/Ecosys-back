package com.plateforme.messaging.service;

import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.messaging.dto.CallSessionDto;
import com.plateforme.messaging.dto.ConversationSummaryDto;
import com.plateforme.messaging.dto.DirectMessageDto;
import com.plateforme.messaging.entity.*;
import com.plateforme.messaging.repository.*;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorResponseTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private DirectMessageRepository directMessageRepository;
    @Mock
    private MessageAttachmentRepository attachmentRepository;
    @Mock
    private ConversationInviteRepository inviteRepository;
    @Mock
    private CallSessionRepository callSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessagingParticipantGuard participantGuard;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CreatorResponseTimeService creatorResponseTimeService;

    @InjectMocks
    private MessagingService messagingService;

    private UUID userId;
    private UUID otherUserId;
    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setFullName("Alice");

        otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setFullName("Bob");
    }

    @Test
    @DisplayName("findOrCreateConversation blocks self-conversation")
    void findOrCreateConversation_blocksSelf() {
        assertThatThrownBy(() -> messagingService.findOrCreateConversation(userId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("yourself");

        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateConversation returns existing conversation")
    void findOrCreateConversation_returnsExisting() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.DIRECT);
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationParticipant selfParticipant = participant(user, conversation);
        ConversationParticipant otherParticipant = participant(otherUser, conversation);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdAndDeletedAtIsNull(otherUserId)).thenReturn(Optional.of(otherUser));
        when(conversationRepository.findDirectConversationsBetweenUsersOrderByUpdatedAtDesc(userId, otherUserId))
                .thenReturn(List.of(conversation));
        when(participantGuard.isParticipantActive(any())).thenAnswer(inv -> {
            ConversationParticipant p = inv.getArgument(0);
            return p.getLeftAt() == null;
        });
        when(participantRepository.findByConversation_IdInWithUser(List.of(conversationId)))
                .thenReturn(List.of(selfParticipant, otherParticipant));
        when(directMessageRepository.findTop30ByConversation_IdOrderBySentAtDesc(conversationId))
                .thenReturn(List.of());
        when(directMessageRepository.countByConversation_IdAndSender_IdNotAndSentAtAfter(
                eq(conversationId), eq(userId), any(LocalDateTime.class)))
                .thenReturn(0L);

        ConversationSummaryDto summary = messagingService.findOrCreateConversation(userId, otherUserId);

        assertThat(summary.id()).isEqualTo(conversationId);
        assertThat(summary.otherUserId()).isEqualTo(otherUserId);
        assertThat(summary.otherUserFullName()).isEqualTo("Bob");
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    @DisplayName("findOrCreateConversation creates new conversation when none exists")
    void findOrCreateConversation_createsNew() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdAndDeletedAtIsNull(otherUserId)).thenReturn(Optional.of(otherUser));
        when(conversationRepository.findDirectConversationsBetweenUsersOrderByUpdatedAtDesc(userId, otherUserId))
                .thenReturn(List.of());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });
        when(participantRepository.save(any(ConversationParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantGuard.isParticipantActive(any())).thenReturn(true);
        when(directMessageRepository.findTop30ByConversation_IdOrderBySentAtDesc(any()))
                .thenReturn(List.of());
        when(directMessageRepository.countByConversation_IdAndSender_IdNotAndSentAtAfter(
                any(), any(), any(LocalDateTime.class)))
                .thenReturn(0L);

        ConversationSummaryDto summary = messagingService.findOrCreateConversation(userId, otherUserId);

        assertThat(summary.otherUserId()).isEqualTo(otherUserId);
        verify(conversationRepository).save(any(Conversation.class));
        verify(participantRepository, org.mockito.Mockito.times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    @DisplayName("assertParticipant throws when user is not participant")
    void assertParticipant_deniesNonParticipant() {
        UUID conversationId = UUID.randomUUID();
        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> messagingService.assertParticipant(conversationId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getMessages requires participant membership")
    void getMessages_requiresParticipant() {
        UUID conversationId = UUID.randomUUID();
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> messagingService.getMessages(conversationId, userId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(directMessageRepository, never()).findByConversation_IdOrderBySentAtDesc(any(), any());
    }

    @Test
    @DisplayName("sendMessage persists and returns DTO")
    void sendMessage_persistsMessage() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        DirectMessage saved = new DirectMessage();
        saved.setId(UUID.randomUUID());
        saved.setConversation(conversation);
        saved.setSender(user);
        saved.setContent("Hello");
        saved.setMessageType(MessageType.TEXT);
        saved.setSentAt(LocalDateTime.now());

        ConversationParticipant selfParticipant = participant(user, conversation);

        when(participantGuard.requireActiveParticipant(conversationId, userId)).thenReturn(selfParticipant);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(directMessageRepository.save(any(DirectMessage.class))).thenReturn(saved);
        when(conversationRepository.save(conversation)).thenReturn(conversation);

        DirectMessageDto dto = messagingService.sendMessage(conversationId, userId, "Hello");

        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.senderId()).isEqualTo(userId);
        assertThat(dto.conversationId()).isEqualTo(conversationId);

        ArgumentCaptor<DirectMessage> captor = ArgumentCaptor.forClass(DirectMessage.class);
        verify(directMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hello");
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId), any(DirectMessageDto.class));
    }

    @Test
    @DisplayName("listConversationsForUser maps summaries")
    void listConversationsForUser_mapsSummaries() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.DIRECT);
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationParticipant selfParticipant = participant(user, conversation);
        ConversationParticipant otherParticipant = participant(otherUser, conversation);

        when(conversationRepository.findAllForUserOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(conversation));
        when(participantGuard.isParticipantActive(any())).thenAnswer(inv -> {
            ConversationParticipant p = inv.getArgument(0);
            return p.getLeftAt() == null;
        });
        when(participantRepository.findByConversation_IdInWithUser(List.of(conversationId)))
                .thenReturn(List.of(selfParticipant, otherParticipant));
        when(directMessageRepository.findTop30ByConversation_IdOrderBySentAtDesc(conversationId))
                .thenReturn(List.of());
        when(directMessageRepository.countByConversation_IdAndSender_IdNotAndSentAtAfter(
                eq(conversationId), eq(userId), any(LocalDateTime.class)))
                .thenReturn(0L);

        List<ConversationSummaryDto> summaries = messagingService.listConversationsForUser(userId);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().otherUserFullName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("getMessages returns paginated history for participant")
    void getMessages_returnsPage() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        DirectMessage message = new DirectMessage();
        message.setId(UUID.randomUUID());
        message.setConversation(conversation);
        message.setSender(user);
        message.setContent("Hi");
        message.setMessageType(MessageType.TEXT);
        message.setSentAt(LocalDateTime.now());

        ConversationParticipant selfParticipant = participant(user, conversation);

        Page<DirectMessage> page = new PageImpl<>(List.of(message));
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, userId))
                .thenReturn(Optional.of(selfParticipant));
        when(participantGuard.requireActiveParticipant(conversationId, userId)).thenReturn(selfParticipant);
        when(directMessageRepository.findByConversation_IdOrderBySentAtDesc(eq(conversationId), any()))
                .thenReturn(page);
        when(attachmentRepository.findByMessage_IdIn(List.of(message.getId()))).thenReturn(List.of());

        Page<DirectMessageDto> result = messagingService.getMessages(conversationId, userId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().content()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("startCall creates RINGING session and broadcasts")
    void startCall_createsRingingAndBroadcasts() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenReturn(participant(user, conversation));
        when(callSessionRepository.findByConversation_IdAndStatusIn(
                conversationId, List.of(CallSessionStatus.RINGING, CallSessionStatus.ACTIVE)))
                .thenReturn(List.of());
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(inv -> {
            CallSession session = inv.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });

        CallSessionDto result = messagingService.startCall(conversationId, userId, CallType.VIDEO);

        assertThat(result.status()).isEqualTo(CallSessionStatus.RINGING);
        assertThat(result.callType()).isEqualTo(CallType.VIDEO);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId + "/call"),
                any(CallSessionDto.class));
    }

    @Test
    @DisplayName("startCall broadcasts ENDED for replaced active sessions")
    void startCall_broadcastsEndedForExistingSession() {
        UUID conversationId = UUID.randomUUID();
        UUID existingCallId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        CallSession existing = new CallSession();
        existing.setId(existingCallId);
        existing.setConversation(conversation);
        existing.setInitiator(user);
        existing.setCallType(CallType.VOICE);
        existing.setStatus(CallSessionStatus.ACTIVE);
        existing.setStartedAt(LocalDateTime.now());

        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenReturn(participant(user, conversation));
        when(callSessionRepository.findByConversation_IdAndStatusIn(
                conversationId, List.of(CallSessionStatus.RINGING, CallSessionStatus.ACTIVE)))
                .thenReturn(List.of(existing));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(inv -> inv.getArgument(0));

        messagingService.startCall(conversationId, userId, CallType.VIDEO);

        assertThat(existing.getStatus()).isEqualTo(CallSessionStatus.ENDED);
        assertThat(existing.getEndedAt()).isNotNull();
        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/conversations/" + conversationId + "/call"),
                any(CallSessionDto.class));
    }

    @Test
    @DisplayName("answerCall rejects initiator answering own call")
    void answerCall_rejectsInitiator() {
        UUID conversationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        CallSession session = new CallSession();
        session.setId(callId);
        session.setConversation(conversation);
        session.setInitiator(user);
        session.setStatus(CallSessionStatus.RINGING);

        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenReturn(participant(user, conversation));
        when(callSessionRepository.findByIdAndConversation_Id(callId, conversationId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> messagingService.answerCall(callId, conversationId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Initiator cannot answer");
    }

    @Test
    @DisplayName("answerCall rejects non-ringing session")
    void answerCall_rejectsNonRinging() {
        UUID conversationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        CallSession session = new CallSession();
        session.setId(callId);
        session.setConversation(conversation);
        session.setInitiator(user);
        session.setStatus(CallSessionStatus.ENDED);

        when(participantGuard.requireActiveParticipant(conversationId, otherUserId))
                .thenReturn(participant(otherUser, conversation));
        when(callSessionRepository.findByIdAndConversation_Id(callId, conversationId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> messagingService.answerCall(callId, conversationId, otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no longer ringing");
    }

    @Test
    @DisplayName("answerCall activates ringing session for callee")
    void answerCall_activatesForCallee() {
        UUID conversationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        CallSession session = new CallSession();
        session.setId(callId);
        session.setConversation(conversation);
        session.setInitiator(user);
        session.setCallType(CallType.VIDEO);
        session.setStatus(CallSessionStatus.RINGING);
        session.setStartedAt(LocalDateTime.now());

        when(participantGuard.requireActiveParticipant(conversationId, otherUserId))
                .thenReturn(participant(otherUser, conversation));
        when(callSessionRepository.findByIdAndConversation_Id(callId, conversationId))
                .thenReturn(Optional.of(session));
        when(callSessionRepository.save(session)).thenReturn(session);

        CallSessionDto result = messagingService.answerCall(callId, conversationId, otherUserId);

        assertThat(result.status()).isEqualTo(CallSessionStatus.ACTIVE);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId + "/call"),
                any(CallSessionDto.class));
    }

    @Test
    @DisplayName("endCall sets ENDED and endedAt")
    void endCall_setsEnded() {
        UUID conversationId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);

        CallSession session = new CallSession();
        session.setId(callId);
        session.setConversation(conversation);
        session.setInitiator(user);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());

        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenReturn(participant(user, conversation));
        when(callSessionRepository.findByIdAndConversation_Id(callId, conversationId))
                .thenReturn(Optional.of(session));
        when(callSessionRepository.save(session)).thenReturn(session);

        CallSessionDto result = messagingService.endCall(callId, conversationId, userId);

        assertThat(result.status()).isEqualTo(CallSessionStatus.ENDED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("createDirectInvite sends notification and stores pending invite")
    void createDirectInvite_sendsNotification() {
        UUID conversationId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.DIRECT);

        User invitee = new User();
        invitee.setId(inviteeId);
        invitee.setFullName("Bob");

        when(participantGuard.requireActiveParticipant(conversationId, userId))
                .thenReturn(participant(user, conversation));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, inviteeId))
                .thenReturn(Optional.empty());
        when(inviteRepository.findByConversation_IdAndInvitee_IdAndStatus(
                conversationId, inviteeId, InviteStatus.PENDING)).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.of(invitee));
        when(participantRepository.findByConversation_IdInWithUser(List.of(conversationId)))
                .thenReturn(List.of(participant(user, conversation)));
        when(inviteRepository.save(any(ConversationInvite.class))).thenAnswer(inv -> {
            ConversationInvite saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        var result = messagingService.createDirectInvite(conversationId, userId, inviteeId, 48);

        assertThat(result.inviterName()).isEqualTo("Alice");
        assertThat(result.conversationId()).isEqualTo(conversationId);
        verify(notificationService).createAndSend(
                eq(inviteeId),
                eq("CONVERSATION_GUEST_INVITE"),
                anyString(),
                anyString(),
                eq("PLATFORM"),
                any(UUID.class),
                eq(conversationId));
    }

    @Test
    @DisplayName("acceptDirectInvite reactivates inactive guest participant instead of inserting duplicate row")
    void acceptDirectInvite_reactivatesInactiveGuestParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle("Test group");

        User invitee = new User();
        invitee.setId(inviteeId);
        invitee.setFullName("Guest User");

        ConversationParticipant inactiveGuest = participant(invitee, conversation);
        inactiveGuest.setRole(ParticipantRole.GUEST);
        inactiveGuest.setLeftAt(LocalDateTime.now().minusHours(2));
        inactiveGuest.setExpiresAt(LocalDateTime.now().minusHours(2));

        ConversationInvite invite = new ConversationInvite();
        invite.setId(inviteId);
        invite.setConversation(conversation);
        invite.setInvitee(invitee);
        invite.setCreatedBy(user);
        invite.setRole(ParticipantRole.GUEST);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusHours(24));
        invite.setMaxUses(1);
        invite.setUseCount(0);

        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, inviteeId))
                .thenReturn(Optional.of(inactiveGuest));
        when(userRepository.findByIdAndDeletedAtIsNull(inviteeId)).thenReturn(Optional.of(invitee));
        when(participantRepository.save(inactiveGuest)).thenAnswer(invocation -> invocation.getArgument(0));
        when(inviteRepository.save(any(ConversationInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.findByConversation_IdInWithUser(List.of(conversationId)))
                .thenReturn(List.of(inactiveGuest, participant(user, conversation)));
        when(directMessageRepository.save(any(DirectMessage.class))).thenAnswer(invocation -> {
            DirectMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setSentAt(LocalDateTime.now());
            return message;
        });
        when(directMessageRepository.findTop30ByConversation_IdOrderBySentAtDesc(conversationId))
                .thenReturn(List.of());

        ConversationSummaryDto summary = messagingService.acceptDirectInvite(inviteId, inviteeId);

        assertThat(summary.id()).isEqualTo(conversationId);
        assertThat(inactiveGuest.getLeftAt()).isNull();
        assertThat(inactiveGuest.getRole()).isEqualTo(ParticipantRole.GUEST);
        assertThat(inactiveGuest.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(participantRepository, times(1)).save(inactiveGuest);
    }

    @Test
    @DisplayName("getMessages for active guest returns only messages from session window")
    void getMessages_activeGuest_filtersToSessionWindow() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);

        LocalDateTime joinedAt = LocalDateTime.now().minusHours(1);
        ConversationParticipant guest = participant(user, conversation);
        guest.setRole(ParticipantRole.GUEST);
        guest.setJoinedAt(joinedAt);
        guest.setExpiresAt(LocalDateTime.now().plusHours(24));

        Pageable pageable = PageRequest.of(0, 50);
        Page<DirectMessage> page = new PageImpl<>(List.of());
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, userId))
                .thenReturn(Optional.of(guest));
        when(participantGuard.isParticipantActive(guest)).thenReturn(true);
        when(directMessageRepository.findByConversation_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
                any(), any(), any()))
                .thenReturn(page);

        messagingService.getMessages(conversationId, userId, pageable);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(directMessageRepository).findByConversation_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
                eq(conversationId), fromCaptor.capture(), eq(pageable));
        assertThat(fromCaptor.getValue()).isEqualTo(joinedAt.minusSeconds(1));
        verify(directMessageRepository, never()).findByConversation_IdAndSentAtBetweenOrderBySentAtDesc(
                any(), any(), any(), any());
        verify(directMessageRepository, never()).findByConversation_IdOrderBySentAtDesc(any(), any());
    }

    @Test
    @DisplayName("getMessages for ended guest allows read-only session history")
    void getMessages_endedGuest_filtersToEndedSessionWindow() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);

        LocalDateTime joinedAt = LocalDateTime.now().minusHours(3);
        LocalDateTime leftAt = LocalDateTime.now().minusHours(1);
        ConversationParticipant guest = participant(user, conversation);
        guest.setRole(ParticipantRole.GUEST);
        guest.setJoinedAt(joinedAt);
        guest.setLeftAt(leftAt);
        guest.setExpiresAt(leftAt);

        Pageable pageable = PageRequest.of(0, 50);
        Page<DirectMessage> page = new PageImpl<>(List.of());
        when(participantRepository.findByConversation_IdAndUser_Id(conversationId, userId))
                .thenReturn(Optional.of(guest));
        when(participantGuard.isParticipantActive(guest)).thenReturn(false);
        when(directMessageRepository.findByConversation_IdAndSentAtBetweenOrderBySentAtDesc(
                any(), any(), any(), any()))
                .thenReturn(page);

        messagingService.getMessages(conversationId, userId, pageable);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(directMessageRepository).findByConversation_IdAndSentAtBetweenOrderBySentAtDesc(
                eq(conversationId), fromCaptor.capture(), toCaptor.capture(), eq(pageable));
        assertThat(fromCaptor.getValue()).isEqualTo(joinedAt.minusSeconds(1));
        assertThat(toCaptor.getValue()).isEqualTo(leftAt.plusSeconds(10));
        verify(directMessageRepository, never()).findByConversation_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
                any(), any(), any());
    }

    private ConversationParticipant participant(User participantUser, Conversation conversation) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setUser(participantUser);
        participant.setConversation(conversation);
        participant.setRole(ParticipantRole.MEMBER);
        return participant;
    }
}
