package com.plateforme.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logger for the default methods of {@link StorageService}, which cannot carry {@code @Slf4j}. */
final class StorageLog {

    static final Logger LOG = LoggerFactory.getLogger(StorageService.class);

    private StorageLog() {}
}
