package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatorProfileReadinessSupportTest {

    @Test
    @DisplayName("missingFields : profil vide → champs requis (role a un défaut)")
    void missingFields_emptyProfile() {
        User user = new User();
        CreatorProfile profile = new CreatorProfile();
        profile.setAppRole(null);

        assertThat(CreatorProfileReadinessSupport.missingFields(user, profile, false))
                .extracting(CreatorProfileReadinessSupport.Field::key)
                .containsExactly(
                        "photo",
                        "address",
                        "phone",
                        "email",
                        "nationality",
                        "link",
                        "name",
                        "role",
                        "location");
    }

    @Test
    @DisplayName("missingFields : contact partiel → address/phone/email manquants séparément")
    void missingFields_partialContact() {
        User user = readyUser();
        CreatorProfile profile = readyProfile();
        profile.setContactPhone(null);
        profile.setContactPhones(List.of());
        profile.setContactAddress(null);
        profile.setContactAddresses(List.of());

        assertThat(CreatorProfileReadinessSupport.missingFields(user, profile, false))
                .extracting(CreatorProfileReadinessSupport.Field::key)
                .containsExactly("address", "phone");
    }

    @Test
    @DisplayName("missingFields : avatar externe / initiales → photo manquante")
    void missingFields_rejectsNonUploadedAvatar() {
        User user = readyUser();
        user.setAvatarUrl("https://lh3.googleusercontent.com/a/letter-avatar");
        CreatorProfile profile = readyProfile();

        assertThat(CreatorProfileReadinessSupport.missingFields(user, profile, false))
                .extracting(CreatorProfileReadinessSupport.Field::key)
                .containsExactly("photo");
    }

    @Test
    @DisplayName("missingFields : specialties requises pour services")
    void missingFields_requireSpecialties() {
        User user = readyUser();
        CreatorProfile profile = readyProfile();

        assertThat(CreatorProfileReadinessSupport.missingFields(user, profile, false)).isEmpty();
        assertThat(CreatorProfileReadinessSupport.missingFields(user, profile, true))
                .extracting(CreatorProfileReadinessSupport.Field::key)
                .containsExactly("specialties");
    }

    @Test
    @DisplayName("requireReady : profil incomplet → PROFILE_INCOMPLETE")
    void requireReady_throws() {
        assertThatThrownBy(() ->
                CreatorProfileReadinessSupport.requireReady(new User(), new CreatorProfile(), false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(CreatorProfileReadinessSupport.CODE_PROFILE_INCOMPLETE);
    }

    @Test
    @DisplayName("introducesNewServices : détecte un nouvel id")
    void introducesNewServices_detectsNew() {
        UUID existingId = UUID.randomUUID();
        List<ProfileServiceDto> existing = List.of(
                new ProfileServiceDto(existingId, 0, "Old", null, 100, null, List.of()));
        List<ProfileServiceDto> incoming = List.of(
                new ProfileServiceDto(existingId, 0, "Old", null, 100, null, List.of()),
                new ProfileServiceDto(null, 1, "New", null, 200, null, List.of()));

        assertThat(CreatorProfileReadinessSupport.introducesNewServices(existing, incoming)).isTrue();
        assertThat(CreatorProfileReadinessSupport.introducesNewServices(
                existing,
                List.of(new ProfileServiceDto(existingId, 0, "Updated", null, 100, null, List.of()))))
                .isFalse();
    }

    private static User readyUser() {
        User user = new User();
        user.setFullName("Ada Lovelace");
        user.setAvatarUrl("http://localhost:8080/api/storage/profiles/public/ada/avatar.webp");
        return user;
    }

    private static CreatorProfile readyProfile() {
        CreatorProfile profile = new CreatorProfile();
        profile.setAppRole("SERVICE_PROVIDER");
        profile.setNationality("FR");
        profile.setContactEmail("ada@example.com");
        profile.setContactPhone("+33123456789");
        profile.setContactAddress("1 Rue Example, Paris");
        profile.setWebsiteUrl("https://example.com");
        profile.setLocationCity("Paris");
        profile.setLocationCountry("FR");
        profile.setProfileLinks(List.of(
                new ProfileLinkDto(UUID.randomUUID(), "WEBSITE", "Site", "https://example.com", 0, null)));
        profile.setContactEmails(List.of(
                new ProfileContactEntryDto(UUID.randomUUID(), 0, "ada@example.com")));
        profile.setContactPhones(List.of(
                new ProfileContactEntryDto(UUID.randomUUID(), 0, "+33123456789")));
        profile.setContactAddresses(List.of(
                new ProfileContactEntryDto(UUID.randomUUID(), 0, "1 Rue Example, Paris")));
        return profile;
    }
}
