package com.plateforme.user.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactVisibilitySettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("defaults include profileStack PUBLIC")
    void defaults_includeProfileStackPublic() {
        ContactVisibilitySettings settings = ContactVisibilitySettings.defaults();
        assertEquals(ContactVisibilityLevel.PUBLIC, settings.profileStack());
    }

    @Test
    @DisplayName("defaultJson includes profileStack key")
    void defaultJson_includesProfileStackKey() {
        assertTrue(ContactVisibilitySettings.defaultJson().contains("\"profileStack\":\"PUBLIC\""));
    }

    @Test
    @DisplayName("fromJson falls back to PUBLIC profileStack when missing")
    void fromJson_fallsBackProfileStackPublic() {
        ContactVisibilitySettings settings = ContactVisibilitySettings.fromJson(
                objectMapper,
                "{\"website\":\"PUBLIC\"}");
        assertEquals(ContactVisibilityLevel.PUBLIC, settings.profileStack());
    }

    @Test
    @DisplayName("toJson round-trips profileStack")
    void toJson_roundTripsProfileStack() throws Exception {
        ContactVisibilitySettings settings = ContactVisibilitySettings.defaults();
        String json = settings.toJson(objectMapper);
        ContactVisibilitySettings parsed = ContactVisibilitySettings.fromJson(objectMapper, json);
        assertEquals(ContactVisibilityLevel.PUBLIC, parsed.profileStack());
    }
}
