package com.plateforme.ecosystem.storage;

import java.util.Locale;
import java.util.UUID;

/** Helpers pour clés objet stables (sans espaces / caractères problématiques dans les URLs). */
public final class StorageObjectKeys {

    private StorageObjectKeys() {}

    public static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "file.bin";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank()) {
            return "file.bin";
        }
        return name;
    }

    public static String uniqueObjectKey(String prefix, UUID scopeId, String originalFileName) {
        String safeName = sanitizeFileName(originalFileName);
        String ext = extensionOf(safeName);
        String stem = stemOf(safeName, ext);
        return prefix + "/" + scopeId + "/" + UUID.randomUUID() + "-" + stem + ext;
    }

    private static String extensionOf(String safeName) {
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String stemOf(String safeName, String ext) {
        if (ext.isEmpty()) {
            return safeName;
        }
        return safeName.substring(0, safeName.length() - ext.length());
    }
}
