package com.plateforme.user.dto;

import java.util.List;
import java.util.UUID;

public record ProfileMediaBlock(
        UUID id,
        int sortOrder,
        String title,
        String organization,
        String text,
        String mediaUrl,
        String mediaType,
        String period,
        List<String> subtitles,
        /** ONGOING | FINISHED — experience blocks only. */
        String status,
        /** Bullet responsibilities / tasks — experience blocks only. */
        List<String> tasks,
        /** Tools / software used on this role — experience blocks only. */
        List<ProfileToolRefDto> tools,
        /** Proof links (repo, case study, social) — experience blocks only. */
        List<ExperienceProofLink> links,
        /** Short aside / caveat — experience blocks only. */
        String remarks,
        /** City / remote — experience blocks only. */
        String location,
        /** FULL_TIME | PART_TIME | CONTRACT | FREELANCE | INTERNSHIP — experience blocks only. */
        String employmentType
) {
    public ProfileMediaBlock(UUID id, int sortOrder, String text, String mediaUrl, String mediaType) {
        this(id, sortOrder, null, null, text, mediaUrl, mediaType, null, List.of(),
                null, List.of(), List.of(), List.of(), null, null, null);
    }

    public ProfileMediaBlock(UUID id, int sortOrder, String text, String mediaUrl, String mediaType,
                             List<String> subtitles) {
        this(id, sortOrder, null, null, text, mediaUrl, mediaType, null, subtitles,
                null, List.of(), List.of(), List.of(), null, null, null);
    }

    public ProfileMediaBlock(UUID id, int sortOrder, String text, String mediaUrl, String mediaType,
                             String period, List<String> subtitles) {
        this(id, sortOrder, null, null, text, mediaUrl, mediaType, period, subtitles,
                null, List.of(), List.of(), List.of(), null, null, null);
    }

    public ProfileMediaBlock {
        subtitles = subtitles != null ? List.copyOf(subtitles) : List.of();
        tasks = tasks != null ? List.copyOf(tasks) : List.of();
        tools = tools != null ? List.copyOf(tools) : List.of();
        links = links != null ? List.copyOf(links) : List.of();
    }
}
