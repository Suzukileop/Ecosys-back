package com.plateforme.messaging.controller;

import com.plateforme.messaging.dto.ConversationSummaryDto;
import com.plateforme.messaging.dto.CreateConversationRequest;
import com.plateforme.messaging.dto.DirectMessageDto;
import com.plateforme.messaging.service.MessagingService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.plateforme.messaging.dto.AddConversationMemberRequest;
import com.plateforme.messaging.dto.AttachmentAccessDto;
import com.plateforme.messaging.dto.CallSessionDto;
import com.plateforme.messaging.dto.ConversationGuestDto;
import com.plateforme.messaging.dto.ConversationInviteDto;
import com.plateforme.messaging.dto.CreateConversationInviteRequest;
import com.plateforme.messaging.dto.CreateDirectInviteRequest;
import com.plateforme.messaging.dto.ConversationParticipantDto;
import com.plateforme.messaging.dto.TemporaryInboxEntryDto;
import com.plateforme.messaging.dto.OutgoingGuestInviteDto;
import com.plateforme.messaging.dto.PendingConversationInviteDto;
import com.plateforme.messaging.dto.CreateGroupConversationRequest;
import com.plateforme.messaging.dto.SendDirectMessageDto;
import com.plateforme.messaging.dto.StartCallRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Messaging", description = "Messagerie directe et groupes")
public class MessagingController {

    private final MessagingService messagingService;
    private final ObjectMapper objectMapper;

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConversationSummaryDto>> listConversations() {
        return ResponseEntity.ok(messagingService.listConversationsForUser(getCurrentUserId()));
    }

    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok(messagingService.findOrCreateConversation(getCurrentUserId(), request.otherUserId()));
    }

    @PostMapping("/conversations/group")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> createGroup(@Valid @RequestBody CreateGroupConversationRequest request)
            throws IOException {
        return ResponseEntity.ok(messagingService.createGroupConversation(
                getCurrentUserId(), request.title(), request.memberIds(), null));
    }

    @PostMapping(value = "/conversations/group", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> createGroupWithCover(
            @RequestParam String title,
            @RequestParam(required = false) String memberIds,
            @RequestParam(value = "cover", required = false) MultipartFile cover) throws IOException {
        return ResponseEntity.ok(messagingService.createGroupConversation(
                getCurrentUserId(), title, parseMemberIds(memberIds), cover));
    }

    @PostMapping(value = "/conversations/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> updateGroupCover(
            @PathVariable("id") UUID conversationId,
            @RequestParam("cover") MultipartFile cover) throws IOException {
        return ResponseEntity.ok(messagingService.updateGroupCover(conversationId, getCurrentUserId(), cover));
    }

    @PostMapping("/conversations/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> addMember(
            @PathVariable("id") UUID conversationId,
            @Valid @RequestBody AddConversationMemberRequest request) {
        return ResponseEntity.ok(messagingService.addMember(conversationId, getCurrentUserId(), request.userId()));
    }

    @GetMapping("/conversations/{id}/participants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConversationParticipantDto>> listParticipants(@PathVariable("id") UUID conversationId) {
        return ResponseEntity.ok(messagingService.listParticipants(conversationId, getCurrentUserId()));
    }

    @GetMapping("/conversations/{id}/guests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConversationGuestDto>> listActiveGuests(@PathVariable("id") UUID conversationId) {
        return ResponseEntity.ok(messagingService.listActiveGuests(conversationId, getCurrentUserId()));
    }

    @GetMapping("/conversations/{id}/invites/outgoing")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OutgoingGuestInviteDto>> listOutgoingGuestInvites(
            @PathVariable("id") UUID conversationId) {
        return ResponseEntity.ok(messagingService.listOutgoingGuestInvites(conversationId, getCurrentUserId()));
    }

    @DeleteMapping("/conversations/{id}/invites/{inviteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelDirectGuestInvite(
            @PathVariable("id") UUID conversationId,
            @PathVariable("inviteId") UUID inviteId) {
        messagingService.cancelDirectGuestInvite(conversationId, getCurrentUserId(), inviteId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{id}/guests/{guestUserId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeGuestAccess(
            @PathVariable("id") UUID conversationId,
            @PathVariable("guestUserId") UUID guestUserId) {
        messagingService.revokeGuestAccess(conversationId, getCurrentUserId(), guestUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/guests/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leaveAsGuest(@PathVariable("id") UUID conversationId) {
        messagingService.leaveAsGuest(conversationId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DirectMessageDto>> getMessages(
            @PathVariable("id") UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messagingService.getMessages(conversationId, getCurrentUserId(), pageable));
    }

    @GetMapping("/users/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<com.plateforme.marketplace.dto.MinimalUserDto>> searchUsers(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 30));
        return ResponseEntity.ok(
                messagingService.searchUsersForInvite(getCurrentUserId(), q, pageable).getContent());
    }

    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DirectMessageDto> sendTextMessage(
            @PathVariable("id") UUID conversationId,
            @Valid @RequestBody SendDirectMessageDto body) {
        return ResponseEntity.ok(messagingService.sendMessage(conversationId, getCurrentUserId(), body.content()));
    }

    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DirectMessageDto> sendFileMessage(
            @PathVariable("id") UUID conversationId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(messagingService.sendFileMessage(conversationId, getCurrentUserId(), content, file));
    }

    @PostMapping("/conversations/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable("id") UUID conversationId) {
        messagingService.markAsRead(conversationId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/attachments/{attachmentId}/view")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttachmentAccessDto> viewAttachment(
            @PathVariable("id") UUID conversationId,
            @PathVariable("attachmentId") UUID attachmentId) throws IOException {
        return ResponseEntity.ok(messagingService.getAttachmentViewUrl(conversationId, attachmentId, getCurrentUserId()));
    }

    @GetMapping("/conversations/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttachmentAccessDto> downloadAttachment(
            @PathVariable("id") UUID conversationId,
            @PathVariable("attachmentId") UUID attachmentId) throws IOException {
        return ResponseEntity.ok(messagingService.getAttachmentDownloadUrl(conversationId, attachmentId, getCurrentUserId()));
    }

    @PostMapping("/conversations/{id}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationInviteDto> createInvite(
            @PathVariable("id") UUID conversationId,
            @RequestBody(required = false) CreateConversationInviteRequest request) {
        int hours = request != null && request.expiresInHours() != null ? request.expiresInHours() : 48;
        int maxUses = request != null && request.maxUses() != null ? request.maxUses() : 5;
        return ResponseEntity.ok(messagingService.createInvite(conversationId, getCurrentUserId(), hours, maxUses));
    }

    @PostMapping("/conversations/{id}/invites/direct")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PendingConversationInviteDto> createDirectInvite(
            @PathVariable("id") UUID conversationId,
            @Valid @RequestBody CreateDirectInviteRequest request) {
        int hours = request.expiresInHours() != null ? request.expiresInHours() : 48;
        return ResponseEntity.ok(messagingService.createDirectInvite(
                conversationId, getCurrentUserId(), request.inviteeUserId(), hours));
    }

    @GetMapping("/invites/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PendingConversationInviteDto>> listPendingInvites() {
        return ResponseEntity.ok(messagingService.listPendingInvitesForUser(getCurrentUserId()));
    }

    @GetMapping("/temporary/inbox")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TemporaryInboxEntryDto>> listTemporaryInbox() {
        return ResponseEntity.ok(messagingService.listTemporaryInbox(getCurrentUserId()));
    }

    @PostMapping("/invites/direct/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> acceptDirectInvite(@PathVariable("id") UUID inviteId) {
        return ResponseEntity.ok(messagingService.acceptDirectInvite(inviteId, getCurrentUserId()));
    }

    @PostMapping("/invites/direct/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> declineDirectInvite(@PathVariable("id") UUID inviteId) {
        messagingService.declineDirectInvite(inviteId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invites/{token}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationSummaryDto> acceptInvite(@PathVariable("token") String token) {
        return ResponseEntity.ok(messagingService.acceptInvite(token, getCurrentUserId()));
    }

    @PostMapping("/conversations/{id}/calls")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CallSessionDto> startCall(
            @PathVariable("id") UUID conversationId,
            @RequestBody(required = false) StartCallRequest request) {
        var callType = request != null ? request.callType() : null;
        return ResponseEntity.ok(messagingService.startCall(conversationId, getCurrentUserId(), callType));
    }

    @PostMapping("/conversations/{id}/calls/{callId}/answer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CallSessionDto> answerCall(
            @PathVariable("id") UUID conversationId,
            @PathVariable("callId") UUID callId) {
        return ResponseEntity.ok(messagingService.answerCall(callId, conversationId, getCurrentUserId()));
    }

    @PostMapping("/conversations/{id}/calls/{callId}/end")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CallSessionDto> endCall(
            @PathVariable("id") UUID conversationId,
            @PathVariable("callId") UUID callId) {
        return ResponseEntity.ok(messagingService.endCall(callId, conversationId, getCurrentUserId()));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }

    private List<UUID> parseMemberIds(String memberIds) {
        if (memberIds == null || memberIds.isBlank()) {
            return List.of();
        }
        try {
            List<String> rawIds = objectMapper.readValue(memberIds, new TypeReference<List<String>>() {});
            List<UUID> parsed = new ArrayList<>();
            for (String rawId : rawIds) {
                if (rawId != null && !rawId.isBlank()) {
                    parsed.add(UUID.fromString(rawId.trim()));
                }
            }
            return parsed;
        } catch (Exception e) {
            throw new BusinessException("INVALID_MEMBER_IDS", "memberIds must be a JSON array of user IDs.");
        }
    }
}
