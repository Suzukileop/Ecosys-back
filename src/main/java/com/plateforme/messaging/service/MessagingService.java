package com.plateforme.messaging.service;

import com.plateforme.ecosystem.storage.StorageObjectKeys;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.messaging.dto.AttachmentAccessDto;
import com.plateforme.messaging.dto.CallSessionDto;
import com.plateforme.messaging.dto.ConversationGuestDto;
import com.plateforme.messaging.dto.ConversationInviteDto;
import com.plateforme.messaging.dto.ConversationParticipantDto;
import com.plateforme.messaging.dto.ConversationReadReceiptDto;
import com.plateforme.messaging.dto.ConversationSummaryDto;
import com.plateforme.messaging.dto.DirectMessageDto;
import com.plateforme.messaging.dto.MessageAttachmentDto;
import com.plateforme.messaging.dto.PendingConversationInviteDto;
import com.plateforme.messaging.dto.TemporaryInboxEntryDto;
import com.plateforme.messaging.dto.OutgoingGuestInviteDto;
import com.plateforme.messaging.entity.*;
import com.plateforme.messaging.repository.*;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorResponseTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

    private static final int ATTACHMENT_URL_EXPIRY_MINUTES = 15;
    private static final long MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024;
    private static final long MAX_GROUP_COVER_BYTES = 5L * 1024 * 1024;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final DirectMessageRepository directMessageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final ConversationInviteRepository inviteRepository;
    private final CallSessionRepository callSessionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessagingParticipantGuard participantGuard;
    private final NotificationService notificationService;
    private final CreatorResponseTimeService creatorResponseTimeService;

    @Transactional
    public ConversationSummaryDto findOrCreateConversation(UUID currentUserId, UUID otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new BusinessException("CONVERSATION_NOT_ALLOWED", "You cannot start a conversation with yourself.");
        }

        User currentUser = requireActiveUser(currentUserId);
        User otherUser = requireActiveUser(otherUserId);

        return conversationRepository
                .findDirectConversationsBetweenUsersOrderByUpdatedAtDesc(currentUserId, otherUserId)
                .stream()
                .findFirst()
                .map(conversation -> toSummary(conversation, currentUserId,
                        loadParticipantsByConversation(conversation.getId())))
                .orElseGet(() -> createDirectConversation(currentUser, otherUser));
    }

    @Transactional
    public ConversationSummaryDto createGroupConversation(
            UUID creatorId,
            String title,
            List<UUID> memberIds,
            MultipartFile cover) throws IOException {
        User creator = requireActiveUser(creatorId);
        String trimmedTitle = title != null ? title.trim() : "";
        if (trimmedTitle.isBlank()) {
            throw new BusinessException("TITLE_REQUIRED", "Group title is required.");
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle(trimmedTitle);
        conversation.setCreatedBy(creator);
        conversationRepository.save(conversation);
        if (cover != null && !cover.isEmpty()) {
            conversation.setCoverUrl(storeGroupCover(conversation.getId(), cover));
            conversationRepository.save(conversation);
        }

        addParticipant(conversation, creator, ParticipantRole.OWNER, null);

        Set<UUID> added = new HashSet<>();
        added.add(creatorId);
        if (memberIds != null) {
            for (UUID memberId : memberIds) {
                if (memberId == null || added.contains(memberId)) continue;
                User member = requireActiveUser(memberId);
                addParticipant(conversation, member, ParticipantRole.MEMBER, null);
                added.add(memberId);
            }
        }

        log.info("Groupe créé id={} par user={} avec {} membres", conversation.getId(), creatorId, added.size());
        return toSummary(conversation, creatorId, loadParticipantsByConversation(conversation.getId()));
    }

    @Transactional
    public ConversationSummaryDto updateGroupCover(UUID conversationId, UUID actorId, MultipartFile cover)
            throws IOException {
        assertParticipant(conversationId, actorId);
        Conversation conversation = requireConversation(conversationId);
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException("NOT_GROUP", "Only group conversations can have a cover image.");
        }

        ConversationParticipant actor = participantGuard.requireActiveParticipant(conversationId, actorId);
        if (actor.getRole() != ParticipantRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only the group owner can update the cover image.");
        }

        conversation.setCoverUrl(storeGroupCover(conversationId, cover));
        conversationRepository.save(conversation);
        return toSummary(conversation, actorId, loadParticipantsByConversation(conversationId));
    }

    @Transactional
    public ConversationSummaryDto addMember(UUID conversationId, UUID actorId, UUID newMemberId) {
        assertParticipant(conversationId, actorId);
        Conversation conversation = requireConversation(conversationId);
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException("NOT_GROUP", "Members can only be added to group conversations.");
        }
        if (participantRepository.findByConversation_IdAndUser_Id(conversationId, newMemberId).isPresent()) {
            throw new BusinessException("ALREADY_MEMBER", "User is already in this conversation.");
        }

        User member = requireActiveUser(newMemberId);
        addParticipant(conversation, member, ParticipantRole.MEMBER, null);

        DirectMessage system = new DirectMessage();
        system.setConversation(conversation);
        system.setSender(requireActiveUser(actorId));
        system.setMessageType(MessageType.SYSTEM);
        system.setContent(member.getFullName() + " joined the conversation.");
        directMessageRepository.save(system);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        broadcastMessage(conversationId, toDto(system, List.of()));
        return toSummary(conversation, actorId, loadParticipantsByConversation(conversationId));
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listConversationsForUser(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllForUserOrderByUpdatedAtDesc(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, List<ConversationParticipant>> participantsByConversation =
                participantRepository.findByConversation_IdInWithUser(conversationIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getConversation().getId()));

        Set<UUID> seenDirectPeers = new HashSet<>();
        Set<UUID> seenGroups = new HashSet<>();
        List<ConversationSummaryDto> result = new ArrayList<>();

        for (Conversation conversation : conversations) {
            List<ConversationParticipant> participants =
                    participantsByConversation.getOrDefault(conversation.getId(), List.of());
            ConversationParticipant self = participants.stream()
                    .filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);
            if (self == null || !participantGuard.isParticipantActive(self)) {
                continue;
            }

            ConversationSummaryDto summary = toSummary(conversation, userId, participants);

            if (conversation.getType() == ConversationType.GROUP) {
                if (seenGroups.add(summary.id())) {
                    result.add(summary);
                }
            } else if (summary.otherUserId() != null && seenDirectPeers.add(summary.otherUserId())) {
                result.add(summary);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<DirectMessageDto> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        ConversationParticipant participant = participantRepository.findByConversation_IdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> {
                    log.warn("Accès refusé à la conversation={} pour user={}", conversationId, userId);
                    return new org.springframework.security.access.AccessDeniedException(
                            "Vous n'êtes pas participant de cette conversation");
                });

        Page<DirectMessage> page;
        if (participant.getRole() == ParticipantRole.GUEST) {
            if (!canGuestReadMessages(participant)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Your access to this conversation has expired.");
            }
            LocalDateTime sessionStart = guestSessionWindowStart(participant);
            LocalDateTime sessionEnd = guestSessionWindowEnd(participant);
            if (sessionEnd == null) {
                page = directMessageRepository.findByConversation_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
                        conversationId, sessionStart, pageable);
            } else {
                page = directMessageRepository.findByConversation_IdAndSentAtBetweenOrderBySentAtDesc(
                        conversationId, sessionStart, sessionEnd, pageable);
            }
        } else {
            participantGuard.requireActiveParticipant(conversationId, userId);
            page = directMessageRepository.findByConversation_IdOrderBySentAtDesc(conversationId, pageable);
        }

        List<UUID> messageIds = page.getContent().stream().map(DirectMessage::getId).toList();
        Map<UUID, List<MessageAttachment>> attachmentsByMessage = loadAttachmentsByMessageIds(messageIds);
        return page.map(msg -> toDto(msg, attachmentsByMessage.getOrDefault(msg.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public Page<com.plateforme.marketplace.dto.MinimalUserDto> searchUsersForInvite(
            UUID currentUserId, String query, Pageable pageable) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 2) {
            return Page.empty(pageable);
        }
        return userRepository.searchByFullNameExcluding(q, currentUserId, pageable)
                .map(u -> new com.plateforme.marketplace.dto.MinimalUserDto(
                        u.getId(), u.getFullName(), u.getAvatarUrl()));
    }

    @Transactional(readOnly = true)
    public List<ConversationParticipantDto> listParticipants(UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        return loadParticipantsByConversation(conversationId).stream()
                .filter(participantGuard::isParticipantActive)
                .map(this::toParticipantDto)
                .sorted(Comparator.comparing(ConversationParticipantDto::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public DirectMessageDto sendMessage(UUID conversationId, UUID senderId, String content) {
        assertParticipant(conversationId, senderId);
        if (content == null || content.isBlank()) {
            throw new BusinessException("CONTENT_REQUIRED", "Message content is required.");
        }
        return persistAndBroadcastText(conversationId, senderId, content.trim());
    }

    @Transactional
    public DirectMessageDto sendFileMessage(UUID conversationId, UUID senderId, String caption, MultipartFile file)
            throws IOException {
        assertParticipant(conversationId, senderId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "File is required.");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Maximum attachment size is 50 MB.");
        }

        User sender = requireActiveUser(senderId);
        Conversation conversation = requireConversation(conversationId);

        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "messaging/private", conversationId, file.getOriginalFilename());
        storageService.uploadPrivateFile(file, objectKey);

        DirectMessage message = new DirectMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setMessageType(MessageType.FILE);
        message.setContent(caption != null && !caption.isBlank() ? caption.trim() : null);
        DirectMessage saved = directMessageRepository.save(message);

        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(saved);
        attachment.setObjectKey(objectKey);
        attachment.setOriginalFilename(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        attachment.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        attachment.setSizeBytes(file.getSize());
        attachmentRepository.save(attachment);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        DirectMessageDto dto = toDto(saved, List.of(attachment));
        broadcastMessage(conversationId, dto);
        triggerCreatorResponseTimeRecalc(sender);
        return dto;
    }

    @Transactional
    public void markAsRead(UUID conversationId, UUID userId) {
        ConversationParticipant participant = participantGuard.requireActiveParticipant(conversationId, userId);
        LocalDateTime readAt = LocalDateTime.now();
        participant.setLastReadAt(readAt);
        participantRepository.save(participant);
        broadcastReadReceipt(conversationId, userId, readAt);
    }

    @Transactional(readOnly = true)
    public AttachmentAccessDto getAttachmentViewUrl(UUID conversationId, UUID attachmentId, UUID userId)
            throws IOException {
        assertParticipant(conversationId, userId);
        MessageAttachment attachment = requireAttachment(conversationId, attachmentId);
        String url = storageService.generateSignedUrl(attachment.getObjectKey(), ATTACHMENT_URL_EXPIRY_MINUTES);
        return new AttachmentAccessDto(url, attachment.getOriginalFilename(), attachment.getContentType());
    }

    @Transactional(readOnly = true)
    public AttachmentAccessDto getAttachmentDownloadUrl(UUID conversationId, UUID attachmentId, UUID userId)
            throws IOException {
        assertParticipant(conversationId, userId);
        MessageAttachment attachment = requireAttachment(conversationId, attachmentId);
        String url = storageService.generateSignedDownloadUrl(
                attachment.getObjectKey(), attachment.getOriginalFilename(), ATTACHMENT_URL_EXPIRY_MINUTES);
        return new AttachmentAccessDto(url, attachment.getOriginalFilename(), attachment.getContentType());
    }

    @Transactional
    public ConversationInviteDto createInvite(UUID conversationId, UUID creatorId, int expiresInHours, int maxUses) {
        assertParticipant(conversationId, creatorId);
        if (expiresInHours <= 0) expiresInHours = 24;
        if (maxUses <= 0) maxUses = 1;

        Conversation conversation = requireConversation(conversationId);
        User creator = requireActiveUser(creatorId);

        ConversationInvite invite = new ConversationInvite();
        invite.setConversation(conversation);
        invite.setToken(generateInviteToken());
        invite.setCreatedBy(creator);
        invite.setRole(ParticipantRole.GUEST);
        invite.setExpiresAt(LocalDateTime.now().plusHours(expiresInHours));
        invite.setMaxUses(maxUses);
        inviteRepository.save(invite);

        return toInviteDto(invite);
    }

    @Transactional
    public PendingConversationInviteDto createDirectInvite(
            UUID conversationId, UUID creatorId, UUID inviteeUserId, int expiresInHours) {
        assertParticipant(conversationId, creatorId);
        if (creatorId.equals(inviteeUserId)) {
            throw new BusinessException("INVITE_SELF", "You cannot invite yourself.");
        }

        Conversation conversation = requireConversation(conversationId);
        if (participantRepository.findByConversation_IdAndUser_Id(conversationId, inviteeUserId)
                .filter(participantGuard::isParticipantActive)
                .isPresent()) {
            throw new BusinessException("ALREADY_MEMBER", "User is already in this conversation.");
        }

        if (inviteRepository.findByConversation_IdAndInvitee_IdAndStatus(
                conversationId, inviteeUserId, InviteStatus.PENDING).isPresent()) {
            throw new BusinessException("INVITE_ALREADY_PENDING", "An invite is already pending for this user.");
        }

        User creator = requireActiveUser(creatorId);
        User invitee = requireActiveUser(inviteeUserId);
        if (expiresInHours <= 0) {
            expiresInHours = 48;
        }

        ConversationInvite invite = new ConversationInvite();
        invite.setConversation(conversation);
        invite.setToken(generateInviteToken());
        invite.setCreatedBy(creator);
        invite.setInvitee(invitee);
        invite.setRole(ParticipantRole.GUEST);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusHours(expiresInHours));
        invite.setMaxUses(1);
        inviteRepository.save(invite);

        String conversationTitle = conversationDisplayName(conversation, creatorId);
        notificationService.createAndSend(
                inviteeUserId,
                "CONVERSATION_GUEST_INVITE",
                "Temporary conversation invite",
                creator.getFullName() + " invited you to join \"" + conversationTitle + "\" temporarily.",
                "PLATFORM",
                invite.getId(),
                conversationId);

        log.info("Direct guest invite id={} conversation={} invitee={} by={}",
                invite.getId(), conversationId, inviteeUserId, creatorId);
        return toPendingInviteDto(invite);
    }

    @Transactional(readOnly = true)
    public List<PendingConversationInviteDto> listPendingInvitesForUser(UUID userId) {
        return inviteRepository.findByInvitee_IdAndStatusOrderByCreatedAtDesc(userId, InviteStatus.PENDING).stream()
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::toPendingInviteDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OutgoingGuestInviteDto> listOutgoingGuestInvites(UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        return inviteRepository.findByConversation_IdAndStatus(conversationId, InviteStatus.PENDING).stream()
                .filter(invite -> invite.getInvitee() != null)
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::toOutgoingGuestInviteDto)
                .toList();
    }

    @Transactional
    public void cancelDirectGuestInvite(UUID conversationId, UUID actorId, UUID inviteId) {
        assertParticipant(conversationId, actorId);
        ConversationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "Invite not found."));
        if (!invite.getConversation().getId().equals(conversationId)) {
            throw new BusinessException("INVITE_NOT_FOUND", "Invite not found.");
        }
        if (invite.getInvitee() == null || invite.getStatus() != InviteStatus.PENDING) {
            throw new BusinessException("INVITE_NOT_PENDING", "This invite is no longer pending.");
        }
        if (invite.getCreatedBy() == null || !invite.getCreatedBy().getId().equals(actorId)) {
            throw new BusinessException("FORBIDDEN", "Only the inviter can cancel this invite.");
        }
        invite.setStatus(InviteStatus.CANCELLED);
        inviteRepository.save(invite);
        log.info("Direct guest invite id={} cancelled by user={}", inviteId, actorId);
    }

    @Transactional(readOnly = true)
    public List<TemporaryInboxEntryDto> listTemporaryInbox(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        List<TemporaryInboxEntryDto> entries = new ArrayList<>();
        Set<UUID> activeGuestConversationIds = new HashSet<>();

        for (PendingConversationInviteDto invite : listPendingInvitesForUser(userId)) {
            entries.add(new TemporaryInboxEntryDto(
                    "INCOMING_INVITE",
                    invite.id(),
                    invite.conversationId(),
                    invite.conversationTitle(),
                    invite.inviterName(),
                    "Invited you as a temporary guest",
                    invite.inviterAvatarUrl(),
                    invite.createdAt(),
                    invite.id(),
                    true));
        }

        for (ConversationInvite invite : inviteRepository
                .findByCreatedBy_IdAndStatusAndInviteeIsNotNullOrderByCreatedAtDesc(userId, InviteStatus.PENDING)) {
            if (invite.getExpiresAt().isBefore(now) || invite.getInvitee() == null) {
                continue;
            }
            Conversation conversation = invite.getConversation();
            entries.add(new TemporaryInboxEntryDto(
                    "OUTGOING_INVITE",
                    invite.getId(),
                    conversation.getId(),
                    conversationDisplayName(conversation, userId),
                    invite.getInvitee().getFullName(),
                    "Waiting for response",
                    invite.getInvitee().getAvatarUrl(),
                    invite.getCreatedAt(),
                    invite.getId(),
                    false));
        }

        for (ConversationSummaryDto summary : listConversationsForUser(userId)) {
            if (!summary.guestSession()) {
                continue;
            }
            activeGuestConversationIds.add(summary.id());
            entries.add(new TemporaryInboxEntryDto(
                    "ACTIVE_GUEST",
                    summary.id(),
                    summary.id(),
                    summary.title() != null && !summary.title().isBlank()
                            ? summary.title()
                            : summary.otherUserFullName(),
                    summary.otherUserFullName(),
                    "Temporary guest access",
                    summary.otherUserAvatarUrl(),
                    summary.lastMessageAt() != null ? summary.lastMessageAt() : summary.updatedAt(),
                    null,
                    true));
        }

        Pageable endedGuestPage = PageRequest.of(0, 30);
        for (ConversationParticipant participation : participantRepository
                .findEndedGuestParticipationsByUserId(userId, endedGuestPage)) {
            Conversation conversation = participation.getConversation();
            if (conversation == null || activeGuestConversationIds.contains(conversation.getId())) {
                continue;
            }
            String title = conversationDisplayName(conversation, userId);
            entries.add(new TemporaryInboxEntryDto(
                    "ENDED_GUEST",
                    participation.getId(),
                    conversation.getId(),
                    title,
                    title,
                    "You left the temporary conversation",
                    conversation.getCoverUrl(),
                    participation.getLeftAt() != null ? participation.getLeftAt() : participation.getJoinedAt(),
                    null,
                    true));
        }

        entries.sort((left, right) -> {
            LocalDateTime leftAt = left.occurredAt() != null ? left.occurredAt() : LocalDateTime.MIN;
            LocalDateTime rightAt = right.occurredAt() != null ? right.occurredAt() : LocalDateTime.MIN;
            return rightAt.compareTo(leftAt);
        });
        return entries;
    }

    @Transactional
    public ConversationSummaryDto acceptDirectInvite(UUID inviteId, UUID userId) {
        ConversationInvite invite = requireDirectInviteForUser(inviteId, userId);
        return acceptInviteInternal(invite, userId);
    }

    @Transactional
    public void declineDirectInvite(UUID inviteId, UUID userId) {
        ConversationInvite invite = requireDirectInviteForUser(inviteId, userId);
        invite.setStatus(InviteStatus.DECLINED);
        inviteRepository.save(invite);
        log.info("Direct guest invite id={} declined by user={}", inviteId, userId);
    }

    @Transactional
    public ConversationSummaryDto acceptInvite(String token, UUID userId) {
        ConversationInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "Invite link is invalid."));

        if (invite.getInvitee() != null && invite.getStatus() == InviteStatus.PENDING) {
            if (!invite.getInvitee().getId().equals(userId)) {
                throw new BusinessException("INVITE_ACCESS_DENIED", "This invite is for another user.");
            }
            return acceptInviteInternal(invite, userId);
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("INVITE_EXPIRED", "This invite link has expired.");
        }
        if (invite.getUseCount() >= invite.getMaxUses()) {
            throw new BusinessException("INVITE_EXHAUSTED", "This invite link has reached its usage limit.");
        }

        UUID conversationId = invite.getConversation().getId();
        if (participantRepository.findByConversation_IdAndUser_Id(conversationId, userId).isPresent()) {
            markAsRead(conversationId, userId);
            return toSummary(requireConversation(conversationId), userId,
                    loadParticipantsByConversation(conversationId));
        }

        return acceptInviteInternal(invite, userId);
    }

    private ConversationSummaryDto acceptInviteInternal(ConversationInvite invite, UUID userId) {
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (invite.getStatus() == InviteStatus.PENDING) {
                invite.setStatus(InviteStatus.CANCELLED);
                inviteRepository.save(invite);
            }
            throw new BusinessException("INVITE_EXPIRED", "This invite has expired.");
        }
        if (invite.getStatus() == InviteStatus.DECLINED || invite.getStatus() == InviteStatus.CANCELLED) {
            throw new BusinessException("INVITE_NOT_PENDING", "This invite is no longer available.");
        }
        if (invite.getInvitee() != null && invite.getStatus() == InviteStatus.PENDING
                && invite.getUseCount() >= invite.getMaxUses()) {
            throw new BusinessException("INVITE_EXHAUSTED", "This invite has already been used.");
        }
        if (invite.getInvitee() == null && invite.getUseCount() >= invite.getMaxUses()) {
            throw new BusinessException("INVITE_EXHAUSTED", "This invite link has reached its usage limit.");
        }

        UUID conversationId = invite.getConversation().getId();
        if (participantRepository.findByConversation_IdAndUser_Id(conversationId, userId)
                .filter(participantGuard::isParticipantActive)
                .isPresent()) {
            if (invite.getStatus() == InviteStatus.PENDING) {
                invite.setStatus(InviteStatus.ACCEPTED);
                invite.setUseCount(Math.min(invite.getUseCount() + 1, invite.getMaxUses()));
                inviteRepository.save(invite);
            }
            markAsRead(conversationId, userId);
            return toSummary(requireConversation(conversationId), userId,
                    loadParticipantsByConversation(conversationId));
        }

        User user = requireActiveUser(userId);
        addParticipant(invite.getConversation(), user, invite.getRole(), invite.getExpiresAt());

        invite.setUseCount(invite.getUseCount() + 1);
        if (invite.getInvitee() != null) {
            invite.setStatus(InviteStatus.ACCEPTED);
        }
        inviteRepository.save(invite);

        DirectMessage system = new DirectMessage();
        system.setConversation(invite.getConversation());
        system.setSender(user);
        system.setMessageType(MessageType.SYSTEM);
        system.setContent(user.getFullName() + " joined as a temporary guest.");
        directMessageRepository.save(system);
        broadcastMessage(conversationId, toDto(system, List.of()));

        return toSummary(invite.getConversation(), userId, loadParticipantsByConversation(conversationId));
    }

    private ConversationInvite requireDirectInviteForUser(UUID inviteId, UUID userId) {
        ConversationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException("INVITE_NOT_FOUND", "Invite not found."));

        if (invite.getInvitee() == null || !invite.getInvitee().getId().equals(userId)) {
            throw new BusinessException("INVITE_ACCESS_DENIED", "This invite is not for you.");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BusinessException("INVITE_NOT_PENDING", "This invite is no longer pending.");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InviteStatus.CANCELLED);
            inviteRepository.save(invite);
            throw new BusinessException("INVITE_EXPIRED", "This invite has expired.");
        }
        return invite;
    }

    @Transactional(readOnly = true)
    public List<ConversationGuestDto> listActiveGuests(UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        Map<UUID, ConversationParticipant> participantsByUserId = loadParticipantsByConversation(conversationId).stream()
                .filter(participantGuard::isParticipantActive)
                .filter(participant -> participant.getUser() != null)
                .collect(Collectors.toMap(
                        participant -> participant.getUser().getId(),
                        participant -> participant,
                        (left, right) -> left));

        return inviteRepository.findByConversation_IdAndStatus(conversationId, InviteStatus.ACCEPTED).stream()
                .filter(invite -> invite.getInvitee() != null)
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(invite -> {
                    ConversationParticipant guest = participantsByUserId.get(invite.getInvitee().getId());
                    return guest != null && guest.getRole() == ParticipantRole.GUEST;
                })
                .map(this::toGuestDto)
                .toList();
    }

    @Transactional
    public void revokeGuestAccess(UUID conversationId, UUID actorId, UUID guestUserId) {
        assertParticipant(conversationId, actorId);
        ConversationInvite invite = inviteRepository
                .findByConversation_IdAndInvitee_IdAndStatus(conversationId, guestUserId, InviteStatus.ACCEPTED)
                .orElseThrow(() -> new BusinessException("GUEST_NOT_FOUND", "No active temporary guest found."));

        if (invite.getCreatedBy() == null || !invite.getCreatedBy().getId().equals(actorId)) {
            throw new BusinessException("FORBIDDEN", "Only the inviter can end this guest's access.");
        }

        User guest = requireActiveUser(guestUserId);
        removeGuestParticipant(conversationId, invite, actorId, guestUserId,
                guest.getFullName() + " left the temporary conversation.");
    }

    @Transactional
    public void leaveAsGuest(UUID conversationId, UUID userId) {
        ConversationParticipant participant = participantGuard.requireActiveParticipant(conversationId, userId);
        if (participant.getRole() != ParticipantRole.GUEST) {
            throw new BusinessException("NOT_GUEST", "Only temporary guests can use this action.");
        }

        ConversationInvite invite = inviteRepository
                .findByConversation_IdAndInvitee_IdAndStatus(conversationId, userId, InviteStatus.ACCEPTED)
                .orElse(null);

        removeGuestParticipant(conversationId, invite, userId, userId,
                requireActiveUser(userId).getFullName() + " left the temporary conversation.");
    }

    private void removeGuestParticipant(
            UUID conversationId,
            ConversationInvite invite,
            UUID actorId,
            UUID guestUserId,
            String systemMessage) {
        ConversationParticipant guestParticipant = participantRepository
                .findByConversation_IdAndUser_Id(conversationId, guestUserId)
                .filter(participantGuard::isParticipantActive)
                .orElseThrow(() -> new BusinessException("GUEST_NOT_FOUND", "Guest is no longer in this conversation."));

        guestParticipant.setLeftAt(LocalDateTime.now());
        guestParticipant.setExpiresAt(LocalDateTime.now());
        participantRepository.save(guestParticipant);

        if (invite != null) {
            invite.setStatus(InviteStatus.CANCELLED);
            inviteRepository.save(invite);
        }

        User actor = requireActiveUser(actorId);
        DirectMessage system = new DirectMessage();
        system.setConversation(requireConversation(conversationId));
        system.setSender(actor);
        system.setMessageType(MessageType.SYSTEM);
        system.setContent(systemMessage);
        directMessageRepository.save(system);
        broadcastMessage(conversationId, toDto(system, List.of()));
    }

    private ConversationGuestDto toGuestDto(ConversationInvite invite) {
        User guest = invite.getInvitee();
        User inviter = invite.getCreatedBy();
        return new ConversationGuestDto(
                invite.getId(),
                guest != null ? guest.getId() : null,
                guest != null ? guest.getFullName() : "Guest",
                guest != null ? guest.getAvatarUrl() : null,
                inviter != null ? inviter.getId() : null,
                inviter != null ? inviter.getFullName() : "Member",
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
    }

    @Transactional
    public CallSessionDto startCall(UUID conversationId, UUID initiatorId, CallType callType) {
        assertParticipant(conversationId, initiatorId);
        CallType type = callType != null ? callType : CallType.VOICE;

        List<CallSession> active = callSessionRepository.findByConversation_IdAndStatusIn(
                conversationId, List.of(CallSessionStatus.RINGING, CallSessionStatus.ACTIVE));
        for (CallSession existing : active) {
            existing.setStatus(CallSessionStatus.ENDED);
            existing.setEndedAt(LocalDateTime.now());
            callSessionRepository.save(existing);
            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + conversationId + "/call",
                    toCallDto(existing));
        }

        User initiator = requireActiveUser(initiatorId);
        Conversation conversation = requireConversation(conversationId);

        CallSession session = new CallSession();
        session.setConversation(conversation);
        session.setInitiator(initiator);
        session.setCallType(type);
        session.setStatus(CallSessionStatus.RINGING);
        callSessionRepository.save(session);

        CallSessionDto dto = toCallDto(session);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/call", dto);
        return dto;
    }

    @Transactional
    public CallSessionDto answerCall(UUID callId, UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        CallSession session = callSessionRepository.findByIdAndConversation_Id(callId, conversationId)
                .orElseThrow(() -> new BusinessException("CALL_NOT_FOUND", "Call session not found."));
        if (session.getStatus() != CallSessionStatus.RINGING) {
            throw new BusinessException("CALL_NOT_RINGING", "Call is no longer ringing.");
        }
        if (session.getInitiator() != null && session.getInitiator().getId().equals(userId)) {
            throw new BusinessException("CALL_CANNOT_ANSWER_OWN", "Initiator cannot answer their own call.");
        }
        session.setStatus(CallSessionStatus.ACTIVE);
        callSessionRepository.save(session);
        CallSessionDto dto = toCallDto(session);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/call", dto);
        return dto;
    }

    @Transactional
    public CallSessionDto endCall(UUID callId, UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        CallSession session = callSessionRepository.findByIdAndConversation_Id(callId, conversationId)
                .orElseThrow(() -> new BusinessException("CALL_NOT_FOUND", "Call session not found."));
        if (session.getStatus() != CallSessionStatus.RINGING && session.getStatus() != CallSessionStatus.ACTIVE) {
            throw new BusinessException("CALL_ALREADY_ENDED", "Call has already ended.");
        }
        session.setStatus(CallSessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);
        CallSessionDto dto = toCallDto(session);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/call", dto);
        return dto;
    }

    public boolean isActiveParticipant(UUID conversationId, UUID userId) {
        return participantGuard.isActiveParticipant(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public void assertParticipant(UUID conversationId, UUID userId) {
        participantGuard.requireActiveParticipant(conversationId, userId);
    }

    public void broadcastMessage(UUID conversationId, DirectMessageDto dto) {
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, dto);
    }

    public void broadcastReadReceipt(UUID conversationId, UUID userId, LocalDateTime readAt) {
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId + "/read",
                new ConversationReadReceiptDto(userId.toString(), readAt.toString()));
    }

    private DirectMessageDto persistAndBroadcastText(UUID conversationId, UUID senderId, String content) {
        User sender = requireActiveUser(senderId);
        Conversation conversation = requireConversation(conversationId);

        DirectMessage message = new DirectMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setMessageType(MessageType.TEXT);
        message.setContent(content);
        DirectMessage saved = directMessageRepository.save(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        DirectMessageDto dto = toDto(saved, List.of());
        broadcastMessage(conversationId, dto);
        triggerCreatorResponseTimeRecalc(sender);
        return dto;
    }

    private void triggerCreatorResponseTimeRecalc(User sender) {
        if (sender == null || sender.getRoles() == null) {
            return;
        }
        boolean isCreator = sender.getRoles().stream()
                .anyMatch(role -> "ROLE_CREATOR".equals(role.getName()));
        if (isCreator) {
            creatorResponseTimeService.recalculateAsync(sender.getId());
        }
    }

    private ConversationSummaryDto createDirectConversation(User currentUser, User otherUser) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(currentUser);
        conversationRepository.save(conversation);

        ConversationParticipant currentParticipant = addParticipant(conversation, currentUser, ParticipantRole.MEMBER, null);
        ConversationParticipant otherParticipant = addParticipant(conversation, otherUser, ParticipantRole.MEMBER, null);

        log.info("Conversation directe créée id={} entre {} et {}",
                conversation.getId(), currentUser.getId(), otherUser.getId());

        return toSummary(conversation, currentUser.getId(), List.of(currentParticipant, otherParticipant));
    }

    private ConversationParticipant addParticipant(
            Conversation conversation, User user, ParticipantRole role, LocalDateTime expiresAt) {
        return participantRepository.findByConversation_IdAndUser_Id(conversation.getId(), user.getId())
                .map(existing -> reactivateParticipant(existing, role, expiresAt))
                .orElseGet(() -> createParticipant(conversation, user, role, expiresAt));
    }

    private ConversationParticipant createParticipant(
            Conversation conversation, User user, ParticipantRole role, LocalDateTime expiresAt) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(role);
        participant.setExpiresAt(expiresAt);
        return participantRepository.save(participant);
    }

    private ConversationParticipant reactivateParticipant(
            ConversationParticipant participant, ParticipantRole role, LocalDateTime expiresAt) {
        participant.setRole(role);
        participant.setExpiresAt(expiresAt);
        participant.setLeftAt(null);
        participant.setJoinedAt(LocalDateTime.now());
        return participantRepository.save(participant);
    }

    private boolean canGuestReadMessages(ConversationParticipant participant) {
        if (participant.getRole() != ParticipantRole.GUEST) {
            return false;
        }
        if (participantGuard.isParticipantActive(participant)) {
            return true;
        }
        return participant.getJoinedAt() != null
                && (participant.getLeftAt() != null || participant.getExpiresAt() != null);
    }

    private LocalDateTime guestSessionWindowStart(ConversationParticipant participant) {
        LocalDateTime joinedAt = participant.getJoinedAt();
        if (joinedAt == null) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        return joinedAt.minusSeconds(1);
    }

    private LocalDateTime guestSessionWindowEnd(ConversationParticipant participant) {
        if (participantGuard.isParticipantActive(participant)) {
            return null;
        }
        LocalDateTime endedAt = participant.getLeftAt();
        if (endedAt == null) {
            endedAt = participant.getExpiresAt();
        }
        return endedAt != null ? endedAt.plusSeconds(10) : null;
    }

    private List<ConversationParticipant> loadParticipantsByConversation(UUID conversationId) {
        return participantRepository.findByConversation_IdInWithUser(List.of(conversationId));
    }

    private Map<UUID, List<MessageAttachment>> loadAttachmentsByMessageIds(List<UUID> messageIds) {
        if (messageIds.isEmpty()) return Map.of();
        return attachmentRepository.findByMessage_IdIn(messageIds).stream()
                .collect(Collectors.groupingBy(a -> a.getMessage().getId()));
    }

    private Conversation requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("CONVERSATION_NOT_FOUND",
                        "Conversation introuvable : " + conversationId));
    }

    private MessageAttachment requireAttachment(UUID conversationId, UUID attachmentId) {
        return attachmentRepository.findByIdAndMessage_Conversation_Id(attachmentId, conversationId)
                .orElseThrow(() -> new BusinessException("ATTACHMENT_NOT_FOUND", "Attachment not found."));
    }

    private String storeGroupCover(UUID conversationId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("COVER_REQUIRED", "Cover image is required.");
        }
        if (file.getSize() > MAX_GROUP_COVER_BYTES) {
            throw new BusinessException("COVER_TOO_LARGE", "Maximum cover image size is 5 MB.");
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!contentType.startsWith("image/")) {
            throw new BusinessException("COVER_INVALID_TYPE", "Cover must be an image file.");
        }

        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "messaging/group-covers/public", conversationId, file.getOriginalFilename());
        return storageService.uploadFile(file, objectKey);
    }

    private User requireActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable : " + userId));
    }

    private ConversationSummaryDto toSummary(
            Conversation conversation,
            UUID currentUserId,
            List<ConversationParticipant> participants) {

        List<ConversationParticipant> active = participants.stream()
                .filter(participantGuard::isParticipantActive)
                .toList();

        ConversationParticipant self = active.stream()
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(currentUserId))
                .findFirst()
                .orElse(null);

        UUID otherUserId = null;
        String otherUserName = null;
        String otherUserAvatar = null;

        if (conversation.getType() == ConversationType.GROUP) {
            otherUserName = conversation.getTitle();
            otherUserAvatar = conversation.getCoverUrl();
        } else {
            ConversationParticipant other = active.stream()
                    .filter(p -> p.getUser() != null && !p.getUser().getId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);
            if (other != null && other.getUser() != null) {
                otherUserId = other.getUser().getId();
                otherUserName = other.getUser().getFullName();
                otherUserAvatar = other.getUser().getAvatarUrl();
            }
        }

        DirectMessage lastMessage = findLastInboxPreviewMessage(conversation.getId());

        String preview = lastMessage != null ? formatPreview(lastMessage) : null;
        UUID lastMessageId = lastMessage != null ? lastMessage.getId() : null;
        UUID lastMessageSenderId = lastMessage != null && lastMessage.getSender() != null
                ? lastMessage.getSender().getId()
                : null;
        LocalDateTime otherUserLastReadAt = null;
        if (conversation.getType() == ConversationType.DIRECT) {
            otherUserLastReadAt = active.stream()
                    .filter(p -> p.getUser() != null && !p.getUser().getId().equals(currentUserId))
                    .map(ConversationParticipant::getLastReadAt)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        long unread = 0;
        if (self != null) {
            LocalDateTime lastRead = self.getLastReadAt();
            unread = directMessageRepository.countByConversation_IdAndSender_IdNotAndSentAtAfter(
                    conversation.getId(),
                    currentUserId,
                    lastRead != null ? lastRead : LocalDateTime.of(1970, 1, 1, 0, 0));
        }

        return new ConversationSummaryDto(
                conversation.getId(),
                conversation.getType(),
                conversation.getTitle(),
                conversation.getCoverUrl(),
                otherUserId,
                otherUserName,
                otherUserAvatar,
                active.size(),
                preview,
                lastMessageId,
                lastMessageSenderId,
                lastMessage != null ? lastMessage.getSentAt() : null,
                otherUserLastReadAt,
                conversation.getUpdatedAt(),
                unread,
                self != null && self.getRole() == ParticipantRole.GUEST,
                self != null ? self.getExpiresAt() : null
        );
    }

    private DirectMessage findLastInboxPreviewMessage(UUID conversationId) {
        for (DirectMessage message : directMessageRepository.findTop30ByConversation_IdOrderBySentAtDesc(conversationId)) {
            if (!isGuestSessionTrace(message)) {
                return message;
            }
        }
        return null;
    }

    private boolean isGuestSessionTrace(DirectMessage message) {
        if (message.getMessageType() != MessageType.SYSTEM) {
            return false;
        }
        String content = message.getContent() != null ? message.getContent().toLowerCase() : "";
        return content.contains("temporary guest")
                || content.contains("temporary conversation")
                || content.contains("temporary guest access");
    }

    private String formatPreview(DirectMessage message) {
        if (message.getMessageType() == MessageType.FILE) {
            return message.getContent() != null && !message.getContent().isBlank()
                    ? "📎 " + message.getContent()
                    : "📎 File";
        }
        if (message.getMessageType() == MessageType.SYSTEM) {
            return message.getContent();
        }
        if (message.getMessageType() == MessageType.CALL) {
            return "📞 Call";
        }
        return message.getContent();
    }

    private DirectMessageDto toDto(DirectMessage message, List<MessageAttachment> attachments) {
        User sender = message.getSender();
        List<MessageAttachmentDto> attachmentDtos = attachments.stream()
                .map(a -> new MessageAttachmentDto(
                        a.getId(),
                        a.getOriginalFilename(),
                        a.getContentType(),
                        a.getSizeBytes()))
                .toList();

        return new DirectMessageDto(
                message.getId(),
                message.getConversation().getId(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getFullName() : null,
                sender != null ? sender.getAvatarUrl() : null,
                message.getContent(),
                message.getMessageType(),
                attachmentDtos,
                message.getSentAt()
        );
    }

    private ConversationParticipantDto toParticipantDto(ConversationParticipant participant) {
        User user = participant.getUser();
        return new ConversationParticipantDto(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                participant.getRole(),
                participant.getJoinedAt(),
                participant.getLastReadAt());
    }

    private ConversationInviteDto toInviteDto(ConversationInvite invite) {
        return new ConversationInviteDto(
                invite.getId(),
                invite.getToken(),
                "/dashboard/discussions/join?token=" + invite.getToken(),
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUseCount()
        );
    }

    private PendingConversationInviteDto toPendingInviteDto(ConversationInvite invite) {
        Conversation conversation = invite.getConversation();
        User creator = invite.getCreatedBy();
        UUID creatorId = creator != null ? creator.getId() : null;
        return new PendingConversationInviteDto(
                invite.getId(),
                conversation.getId(),
                conversation.getType(),
                conversationDisplayName(conversation, creatorId),
                creator != null ? creator.getFullName() : "Member",
                creator != null ? creator.getAvatarUrl() : null,
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
    }

    private OutgoingGuestInviteDto toOutgoingGuestInviteDto(ConversationInvite invite) {
        User invitee = invite.getInvitee();
        return new OutgoingGuestInviteDto(
                invite.getId(),
                invitee != null ? invitee.getId() : null,
                invitee != null ? invitee.getFullName() : "Guest",
                invitee != null ? invitee.getAvatarUrl() : null,
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
    }

    private String conversationDisplayName(Conversation conversation, UUID perspectiveUserId) {
        if (conversation.getType() == ConversationType.GROUP) {
            String title = conversation.getTitle();
            return title != null && !title.isBlank() ? title.trim() : "Group";
        }
        List<ConversationParticipant> participants = loadParticipantsByConversation(conversation.getId());
        String names = participants.stream()
                .filter(participantGuard::isParticipantActive)
                .map(ConversationParticipant::getUser)
                .filter(java.util.Objects::nonNull)
                .filter(u -> perspectiveUserId == null || !u.getId().equals(perspectiveUserId))
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        return names.isBlank() ? "Conversation" : names;
    }

    private CallSessionDto toCallDto(CallSession session) {
        User initiator = session.getInitiator();
        return new CallSessionDto(
                session.getId(),
                session.getConversation().getId(),
                initiator != null ? initiator.getId() : null,
                initiator != null ? initiator.getFullName() : null,
                session.getCallType(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    private static String generateInviteToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
