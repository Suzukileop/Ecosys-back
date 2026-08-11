package com.plateforme.admin.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorProfileDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorAdminServiceTest {

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorProfileService creatorProfileService;

    @InjectMocks
    private CreatorAdminService creatorAdminService;

    @Test
    @DisplayName("setVerified : utilisateur sans rôle CREATOR → BusinessException")
    void setVerified_requiresCreatorRole() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRoles(Set.of());

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> creatorAdminService.setVerified(userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ROLE_REQUIRED");
    }

    @Test
    @DisplayName("setVerified : met à jour le profil")
    void setVerified_updatesProfile() {
        UUID userId = UUID.randomUUID();
        Role creatorRole = new Role();
        creatorRole.setName("ROLE_CREATOR");

        User user = new User();
        user.setId(userId);
        user.setRoles(Set.of(creatorRole));

        CreatorProfile profile = new CreatorProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setIsVerified(false);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.save(profile)).thenReturn(profile);
        CreatorProfileDto profileDto = org.mockito.Mockito.mock(CreatorProfileDto.class);
        when(profileDto.isVerified()).thenReturn(true);
        when(creatorProfileService.getMyProfile(userId)).thenReturn(profileDto);

        var dto = creatorAdminService.setVerified(userId, true);

        assertThat(dto.isVerified()).isTrue();
        verify(creatorProfileRepository).save(profile);
    }
}
