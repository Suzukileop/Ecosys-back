package com.plateforme.admin.controller;

import com.plateforme.admin.service.UserAdminService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.dto.UpdateRoleRequest;
import com.plateforme.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "API d'administration des utilisateurs")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Lister tous les utilisateurs", description = "Retourne la liste paginée des utilisateurs actifs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des utilisateurs"),
            @ApiResponse(responseCode = "403", description = "Accès refusé - rôle ADMIN requis")
    })
    @GetMapping("/users")
    public ResponseEntity<PagedResponse<UserDto>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserDto> usersPage = userAdminService.getAllUsers(pageable);
        return ResponseEntity.ok(PagedResponse.fromPage(usersPage));
    }

    @Operation(summary = "Obtenir un utilisateur", description = "Retourne les détails d'un utilisateur par son ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur trouvé"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
            @ApiResponse(responseCode = "403", description = "Accès refusé - rôle ADMIN requis")
    })
    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.getUserById(id));
    }

    @Operation(summary = "Mettre à jour les rôles", description = "Modifie les rôles d'un utilisateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rôles mis à jour"),
            @ApiResponse(responseCode = "400", description = "Rôles invalides"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
            @ApiResponse(responseCode = "403", description = "Accès refusé - rôle ADMIN requis")
    })
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserDto> updateUserRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(userAdminService.updateUserRoles(id, request));
    }

    @Operation(summary = "Désactiver un utilisateur", description = "Soft delete d'un utilisateur (deleted_at = now())")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Utilisateur désactivé"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
            @ApiResponse(responseCode = "403", description = "Accès refusé - rôle ADMIN requis")
    })
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> disableUser(@PathVariable UUID id) {
        userAdminService.disableUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Réactiver un utilisateur", description = "Réactive un utilisateur précédemment désactivé")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur réactivé"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
            @ApiResponse(responseCode = "403", description = "Accès refusé - rôle ADMIN requis")
    })
    @PostMapping("/users/{id}/enable")
    public ResponseEntity<UserDto> enableUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.enableUser(id));
    }
}
