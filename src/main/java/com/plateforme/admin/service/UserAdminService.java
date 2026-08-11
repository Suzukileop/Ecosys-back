package com.plateforme.admin.service;

import com.plateforme.shared.entity.AuditLog;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.repository.AuditLogRepository;
import com.plateforme.user.dto.UpdateRoleRequest;
import com.plateforme.user.dto.UserDto;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.RoleRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable) {
        return userRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::toUserDto);
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        User user = findActiveUserById(id);
        return toUserDto(user);
    }

    @Transactional
    public UserDto updateUserRoles(UUID id, UpdateRoleRequest request) {
        User user = findActiveUserById(id);
        User actor = getCurrentActor();

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : request.roles()) {
            String fullRoleName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
            Role role = roleRepository.findByName(fullRoleName)
                    .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Rôle introuvable: " + fullRoleName));
            newRoles.add(role);
        }

        user.setRoles(newRoles);
        user = userRepository.save(user);

        saveAuditLog(actor, "UPDATE_ROLES", user,
                "Rôles mis à jour: " + request.roles());
        log.info("Rôles de l'utilisateur {} mis à jour par {}", user.getEmail(),
                actor != null ? actor.getEmail() : "system");

        return toUserDto(user);
    }

    @Transactional
    public void disableUser(UUID id) {
        User user = findActiveUserById(id);
        User actor = getCurrentActor();

        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        saveAuditLog(actor, "DISABLE_USER", user, "Utilisateur désactivé (soft delete)");
        log.info("Utilisateur {} désactivé par {}", user.getEmail(),
                actor != null ? actor.getEmail() : "system");
    }

    @Transactional
    public UserDto enableUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));

        User actor = getCurrentActor();

        user.setDeletedAt(null);
        user = userRepository.save(user);

        saveAuditLog(actor, "ENABLE_USER", user, "Utilisateur réactivé");
        log.info("Utilisateur {} réactivé par {}", user.getEmail(),
                actor != null ? actor.getEmail() : "system");

        return toUserDto(user);
    }

    private User findActiveUserById(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));
    }

    private User getCurrentActor() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveAuditLog(User actor, String action, User target, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setTargetUser(target);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }

    private UserDto toUserDto(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                roleNames,
                user.getCreatedAt(),
                user.getEmailVerified()
        );
    }
}
