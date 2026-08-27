package com.plateforme.user.service;

import com.plateforme.auth.service.AuthService;
import com.plateforme.ecosystem.storage.StorageObjectKeys;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.UpdateUserProfileDto;
import com.plateforme.user.dto.UserDto;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.support.UsernameSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthService authService;
    private final CreatorProfileImageService creatorProfileImageService;

    @Transactional(readOnly = true)
    public UserDto getMyProfile(UUID userId) {
        User user = requireUser(userId);
        return authService.toUserDto(user);
    }

    @Transactional
    public UserDto updateMyProfile(UUID userId, UpdateUserProfileDto dto) {
        User user = requireUser(userId);
        if (dto.fullName() != null) {
            String trimmed = dto.fullName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException("INVALID_FULL_NAME", "Full name cannot be empty");
            }
            user.setFullName(trimmed);
        }
        if (dto.username() != null) {
            String username = UsernameSupport.normalize(dto.username());
            UsernameSupport.requireAvailable(userRepository, username, userId);
            user.setPublicUsername(username);
        }
        user = userRepository.save(user);
        log.info("User profile updated user={}", userId);
        return authService.toUserDto(user);
    }

    @Transactional
    public UserDto uploadAvatar(UUID userId, MultipartFile file) throws IOException {
        User user = requireUser(userId);
        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "profiles/public", userId, file.getOriginalFilename());
        String url = storageService.uploadFile(file, objectKey);
        // Keep previous and new URLs in history (never drop prior profile photos).
        creatorProfileImageService.record(userId, user.getAvatarUrl());
        creatorProfileImageService.record(userId, url);
        user.setAvatarUrl(url);
        user = userRepository.save(user);
        log.info("Avatar uploaded user={}", userId);
        return authService.toUserDto(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + userId));
    }
}
