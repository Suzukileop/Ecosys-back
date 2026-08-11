package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.CreatorFollow;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorFollowServiceTest {

    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreatorFollowService creatorFollowService;

    private UUID followerId;
    private UUID creatorId;
    private User follower;
    private User creator;

    @BeforeEach
    void setUp() {
        followerId = UUID.randomUUID();
        creatorId = UUID.randomUUID();

        follower = new User();
        follower.setId(followerId);
        follower.setFullName("Client");

        creator = new User();
        creator.setId(creatorId);
        creator.setFullName("Creator");
        Role role = new Role();
        role.setName("ROLE_CREATOR");
        creator.setRoles(Set.of(role));
    }

    @Test
    @DisplayName("follow creates relationship for any authenticated user")
    void follow_createsRelationship() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findByIdAndDeletedAtIsNull(followerId)).thenReturn(Optional.of(follower));
        when(creatorFollowRepository.existsByFollower_IdAndCreator_Id(followerId, creatorId)).thenReturn(false);
        when(creatorFollowRepository.save(any(CreatorFollow.class))).thenAnswer(inv -> inv.getArgument(0));

        creatorFollowService.follow(followerId, creatorId);

        verify(creatorFollowRepository).save(any(CreatorFollow.class));
    }

    @Test
    @DisplayName("follow blocks self-follow")
    void follow_blocksSelf() {
        assertThatThrownBy(() -> creatorFollowService.follow(creatorId, creatorId))
                .isInstanceOf(BusinessException.class);
        verify(creatorFollowRepository, never()).save(any());
    }

    @Test
    @DisplayName("getStats returns follower count and following state")
    void getStats_returnsCounts() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(creatorFollowRepository.countByCreator_Id(creatorId)).thenReturn(12L);
        when(creatorFollowRepository.existsByFollower_IdAndCreator_Id(followerId, creatorId)).thenReturn(true);

        var stats = creatorFollowService.getStats(creatorId, followerId);

        assertThat(stats.followerCount()).isEqualTo(12);
        assertThat(stats.isFollowing()).isTrue();
    }
}
