package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorProfileImageDto;
import com.plateforme.user.entity.CreatorProfileImage;
import com.plateforme.user.entity.CreatorProfileImageKind;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileImageRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorProfileImageService {

    private final CreatorProfileImageRepository imageRepository;
    private final UserRepository userRepository;

    /** Persist a used avatar URL so later replacements keep a visible history. */
    @Transactional
    public void record(UUID userId, String url) {
        if (userId == null) {
            return;
        }
        String cleaned = url == null ? null : url.trim();
        if (cleaned == null || cleaned.isEmpty()) {
            return;
        }

        if (imageRepository.findByUserIdAndKindAndUrl(userId, CreatorProfileImageKind.AVATAR, cleaned).isPresent()) {
            return;
        }

        CreatorProfileImage row = new CreatorProfileImage();
        row.setUserId(userId);
        row.setKind(CreatorProfileImageKind.AVATAR);
        row.setUrl(cleaned);
        imageRepository.save(row);
        log.debug("Recorded profile avatar history user={}", userId);
    }

    @Transactional
    public List<CreatorProfileImageDto> listForCreator(UUID userId) {
        User user = requireCreator(userId);
        ensureCurrentSeeded(user);

        String currentAvatar = blankToNull(user.getAvatarUrl());
        return imageRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(row -> toDto(row, currentAvatar))
                .toList();
    }

    @Transactional
    public CreatorProfileImageDto restore(UUID userId, UUID imageId) {
        User user = requireCreator(userId);
        CreatorProfileImage image = imageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new BusinessException("IMAGE_NOT_FOUND", "Image introuvable"));

        user.setAvatarUrl(image.getUrl());
        userRepository.save(user);

        return toDto(image, blankToNull(user.getAvatarUrl()));
    }

    private void ensureCurrentSeeded(User user) {
        record(user.getId(), user.getAvatarUrl());
    }

    private CreatorProfileImageDto toDto(CreatorProfileImage row, String currentAvatar) {
        return new CreatorProfileImageDto(
                row.getId(),
                row.getKind(),
                row.getUrl(),
                row.getCreatedAt(),
                Objects.equals(row.getUrl(), currentAvatar)
        );
    }

    private User requireCreator(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));
        boolean isCreator = user.getRoles() != null
                && user.getRoles().stream().anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!isCreator) {
            throw new BusinessException("FORBIDDEN", "Creator role required");
        }
        return user;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
