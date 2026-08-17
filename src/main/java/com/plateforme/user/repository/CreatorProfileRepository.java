package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    Optional<CreatorProfile> findByUserId(UUID userId);

    List<CreatorProfile> findByUser_IdIn(Collection<UUID> userIds);

    @Query("""
            SELECT cp FROM CreatorProfile cp JOIN cp.user u
            WHERE u.id = :userId AND u.deletedAt IS NULL
            """)
    Optional<CreatorProfile> findByUserIdAndUserDeletedAtIsNull(@Param("userId") UUID userId);

    Page<CreatorProfile> findBySpecialiteContainingIgnoreCaseAndUser_DeletedAtIsNull(String specialite, Pageable pageable);

    /**
     * Keyword search with weighted relevance (specialties/tags first).
     * :qCanonical / :qExpanded / :specialiteAlt / :specialiteSignals may be '' when unused.
     */
    @Query(value = """
            SELECT cp.* FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR (
                    CAST(:qCanonical AS VARCHAR) <> ''
                    AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    )
                 )
                 OR (
                   CAST(:qExpanded AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:qExpanded AS VARCHAR), '|')) AS x(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            ORDER BY
              GREATEST(
                CASE
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  THEN 100
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  ) THEN 80
                  WHEN (
                    jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) > 0
                    AND LOWER(cp.specialties->>0) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  ) OR (
                    jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) = 0
                    AND LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  ) THEN 55
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  ) THEN 40
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  ) THEN 35
                  WHEN LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  THEN 30
                  WHEN LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  THEN 25
                  WHEN LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) THEN 10
                  ELSE 0
                END,
                CASE
                  WHEN CAST(:qCanonical AS VARCHAR) = '' THEN 0
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  THEN 100
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  ) THEN 80
                  WHEN (
                    jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) > 0
                    AND LOWER(cp.specialties->>0) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  ) OR (
                    jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) = 0
                    AND LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  ) THEN 55
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  ) THEN 40
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  ) THEN 35
                  WHEN LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  THEN 30
                  WHEN LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                  THEN 25
                  WHEN LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%')) THEN 10
                  ELSE 0
                END
              ) DESC,
              COALESCE(cp.is_verified, false) DESC,
              COALESCE(cp.is_available, true) DESC,
              u.full_name ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR (
                    CAST(:qCanonical AS VARCHAR) <> ''
                    AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    )
                 )
                 OR (
                   CAST(:qExpanded AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:qExpanded AS VARCHAR), '|')) AS x(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            """,
            nativeQuery = true)
    Page<CreatorProfile> searchByBioOrSpecialite(
            @Param("q") String q,
            @Param("qCanonical") String qCanonical,
            @Param("qExpanded") String qExpanded,
            @Param("verified") Boolean verified,
            @Param("available") Boolean available,
            @Param("nationality") String nationality,
            @Param("specialite") String specialite,
            @Param("specialiteAlt") String specialiteAlt,
            @Param("specialiteSignals") String specialiteSignals,
            @Param("minYearsExperience") Integer minYearsExperience,
            Pageable pageable);

    @Query(value = """
            SELECT cp.* FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            ORDER BY
              CASE
                WHEN CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = '' THEN 0
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                  WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                THEN 100
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                  WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                ) THEN 80
                WHEN (
                  jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) > 0
                  AND LOWER(cp.specialties->>0) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                ) OR (
                  jsonb_array_length(COALESCE(cp.specialties, '[]'::jsonb)) = 0
                  AND LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                ) THEN 55
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                  WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                ) THEN 40
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                  WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                ) THEN 35
                ELSE 0
              END DESC,
              CASE
                WHEN CAST(:specialiteAlt AS VARCHAR) = '' THEN 0
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                  WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                THEN 100
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                  WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                ) THEN 80
                ELSE 0
              END DESC,
              CASE
                WHEN CAST(:specialiteSignals AS VARCHAR) = '' THEN 0
                WHEN EXISTS (
                  SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                  WHERE length(btrim(term)) >= 2
                    AND (
                      LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                      OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                    )
                ) THEN 20
                ELSE 0
              END DESC,
              COALESCE(cp.is_verified, false) DESC,
              COALESCE(cp.is_available, true) DESC,
              u.full_name ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            """,
            nativeQuery = true)
    Page<CreatorProfile> findForMarketplace(
            @Param("specialite") String specialite,
            @Param("specialiteAlt") String specialiteAlt,
            @Param("specialiteSignals") String specialiteSignals,
            @Param("verified") Boolean verified,
            @Param("available") Boolean available,
            @Param("nationality") String nationality,
            @Param("minYearsExperience") Integer minYearsExperience,
            Pageable pageable);

    @Query(value = """
            SELECT cp.* FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            ORDER BY
              CASE WHEN cp.location_lat IS NULL OR cp.location_lng IS NULL THEN 1 ELSE 0 END ASC,
              (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                  cos(radians(CAST(:lat AS DOUBLE PRECISION)))
                  * cos(radians(cp.location_lat))
                  * cos(radians(cp.location_lng) - radians(CAST(:lng AS DOUBLE PRECISION)))
                  + sin(radians(CAST(:lat AS DOUBLE PRECISION)))
                  * sin(radians(cp.location_lat))
              )))) ASC NULLS LAST,
              CASE
                WHEN CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = '' THEN 0
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                  WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                THEN 100
                WHEN EXISTS (
                  SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                  WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                ) THEN 80
                ELSE 0
              END DESC,
              COALESCE(cp.is_verified, false) DESC,
              u.full_name ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            """,
            nativeQuery = true)
    Page<CreatorProfile> findForMarketplaceByDistance(
            @Param("specialite") String specialite,
            @Param("specialiteAlt") String specialiteAlt,
            @Param("specialiteSignals") String specialiteSignals,
            @Param("verified") Boolean verified,
            @Param("available") Boolean available,
            @Param("nationality") String nationality,
            @Param("minYearsExperience") Integer minYearsExperience,
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable);

    @Query(value = """
            SELECT cp.* FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR (
                    CAST(:qCanonical AS VARCHAR) <> ''
                    AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    )
                 )
                 OR (
                   CAST(:qExpanded AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:qExpanded AS VARCHAR), '|')) AS x(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            ORDER BY
              CASE WHEN cp.location_lat IS NULL OR cp.location_lng IS NULL THEN 1 ELSE 0 END ASC,
              (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                  cos(radians(CAST(:lat AS DOUBLE PRECISION)))
                  * cos(radians(cp.location_lat))
                  * cos(radians(cp.location_lng) - radians(CAST(:lng AS DOUBLE PRECISION)))
                  + sin(radians(CAST(:lat AS DOUBLE PRECISION)))
                  * sin(radians(cp.location_lat))
              )))) ASC NULLS LAST,
              GREATEST(
                CASE
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  THEN 100
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                  ) THEN 80
                  ELSE 0
                END,
                CASE
                  WHEN CAST(:qCanonical AS VARCHAR) = '' THEN 0
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  ) OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  THEN 100
                  WHEN EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:qCanonical AS VARCHAR)))
                  ) THEN 80
                  ELSE 0
                END
              ) DESC,
              COALESCE(cp.is_verified, false) DESC,
              u.full_name ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM creator_profiles cp
            INNER JOIN users u ON u.id = cp.user_id
            WHERE u.deleted_at IS NULL
            AND COALESCE(cp.app_role, 'GENERAL_MEMBER') <> 'RH_RECRUITER'
            AND (
                 COALESCE(cp.app_role, 'GENERAL_MEMBER') IN ('SERVICE_PROVIDER', 'FREELANCER_STUDENT')
                 OR jsonb_array_length(COALESCE(cp.profile_services, '[]'::jsonb)) > 0
            )
            AND (
                 LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 )
                 OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                 OR (
                    CAST(:qCanonical AS VARCHAR) <> ''
                    AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         )
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%'))
                    )
                 )
                 OR (
                   CAST(:qExpanded AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:qExpanded AS VARCHAR), '|')) AS x(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(u.full_name, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            AND (CAST(:verified AS BOOLEAN) IS NULL OR cp.is_verified = CAST(:verified AS BOOLEAN))
            AND (CAST(:available AS BOOLEAN) IS NULL OR COALESCE(cp.is_available, true) = CAST(:available AS BOOLEAN))
            AND (CAST(:nationality AS VARCHAR) IS NULL OR CAST(:nationality AS VARCHAR) = ''
                 OR UPPER(cp.nationality) = UPPER(CAST(:nationality AS VARCHAR)))
            AND (CAST(:minYearsExperience AS INTEGER) IS NULL
                 OR cp.years_of_experience >= CAST(:minYearsExperience AS INTEGER))
            AND (
                 CAST(:specialite AS VARCHAR) IS NULL OR CAST(:specialite AS VARCHAR) = ''
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                    WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR POSITION(LOWER(TRIM(CAST(:specialite AS VARCHAR))) IN LOWER(spec)) > 0
                       OR (
                            length(regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                            AND POSITION(
                                regexp_replace(LOWER(TRIM(CAST(:specialite AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                            ) > 0
                       )
                 )
                 OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                    WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialite AS VARCHAR)))
                       OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialite AS VARCHAR), '%'))
                 )
                 OR (
                    CAST(:specialiteAlt AS VARCHAR) <> ''
                    AND (
                         EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) spec
                            WHERE LOWER(TRIM(spec)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR POSITION(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))) IN LOWER(spec)) > 0
                               OR (
                                    length(regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')) > 0
                                    AND POSITION(
                                        regexp_replace(LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR))), '[^a-z0-9]+', '', 'g')
                                        IN regexp_replace(LOWER(spec), '[^a-z0-9]+', '', 'g')
                                    ) > 0
                               )
                         )
                         OR LOWER(TRIM(COALESCE(cp.specialite, ''))) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         OR EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.specialty_tags, '[]'::jsonb)) tag
                            WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:specialiteAlt AS VARCHAR)))
                               OR LOWER(tag) LIKE LOWER(CONCAT('%', CAST(:specialiteAlt AS VARCHAR), '%'))
                         )
                    )
                 )
                 OR (
                   CAST(:specialiteSignals AS VARCHAR) <> ''
                   AND EXISTS (
                     SELECT 1 FROM unnest(string_to_array(CAST(:specialiteSignals AS VARCHAR), '|')) AS sig(term)
                     WHERE length(btrim(term)) >= 2
                       AND (
                         LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_description, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.shop_selling_focus, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialties::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.specialty_tags::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.strengths_tools_mastered::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                         OR LOWER(COALESCE(cp.profile_services::text, '')) LIKE LOWER(CONCAT('%', btrim(term), '%'))
                       )
                   )
                 )
            )
            """,
            nativeQuery = true)
    Page<CreatorProfile> searchByBioOrSpecialiteByDistance(
            @Param("q") String q,
            @Param("qCanonical") String qCanonical,
            @Param("qExpanded") String qExpanded,
            @Param("verified") Boolean verified,
            @Param("available") Boolean available,
            @Param("nationality") String nationality,
            @Param("specialite") String specialite,
            @Param("specialiteAlt") String specialiteAlt,
            @Param("specialiteSignals") String specialiteSignals,
            @Param("minYearsExperience") Integer minYearsExperience,
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable);

    @Query(value = """
            SELECT spec FROM (
              SELECT DISTINCT TRIM(elem) AS spec
              FROM creator_profiles cp
              INNER JOIN users u ON u.id = cp.user_id
              CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(cp.specialties, '[]'::jsonb)) AS elem
              WHERE u.deleted_at IS NULL
                AND TRIM(elem) <> ''
              UNION
              SELECT DISTINCT TRIM(cp.specialite) AS spec
              FROM creator_profiles cp
              INNER JOIN users u ON u.id = cp.user_id
              WHERE u.deleted_at IS NULL
                AND cp.specialite IS NOT NULL
                AND TRIM(cp.specialite) <> ''
            ) t
            WHERE CAST(:q AS VARCHAR) IS NULL OR CAST(:q AS VARCHAR) = ''
               OR LOWER(spec) LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) ESCAPE '\\'
            ORDER BY spec ASC
            LIMIT 40
            """, nativeQuery = true)
    List<String> suggestSpecialties(@Param("q") String q);
}
