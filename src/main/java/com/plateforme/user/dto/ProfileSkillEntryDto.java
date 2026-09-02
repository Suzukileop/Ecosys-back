package com.plateforme.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.UUID;

@JsonDeserialize(using = ProfileSkillEntryDtoDeserializer.class)
public record ProfileSkillEntryDto(
        UUID id,
        int sortOrder,
        String title,
        String description
) {}
