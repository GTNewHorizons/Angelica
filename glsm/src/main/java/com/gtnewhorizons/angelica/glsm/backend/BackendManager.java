package com.gtnewhorizons.angelica.glsm.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class BackendManager {
    private static final Logger LOGGER = LogManager.getLogger("BackendManager");

    public static final RenderBackend RENDER_BACKEND = loadService();

    private BackendManager() {}

    public static void init() {
        RENDER_BACKEND.init();
        LOGGER.info("Initialized backend: {} (priority {})", RENDER_BACKEND.getName(), RENDER_BACKEND.getPriority());
    }

    public static void shutdown() {
        RENDER_BACKEND.shutdown();
    }

    private static RenderBackend loadService() {
        final ServiceLoader<RenderBackend> loader = ServiceLoader.load(RenderBackend.class, RenderBackend.class.getClassLoader());

        RenderBackend best = null;
        final Iterator<RenderBackend> iterator = loader.iterator();
        int attempts = 0;

        while (attempts < 16) {
            try {
                attempts++;
                if (!iterator.hasNext()) break;
                final RenderBackend backend = iterator.next();
                LOGGER.info("Found backend: {} (priority {})", backend.getName(), backend.getPriority());
                if (backend.isAvailable() && (best == null || backend.getPriority() > best.getPriority())) {
                    best = backend;
                }
            } catch (ServiceConfigurationError | LinkageError e) {
                LOGGER.debug("Skipping unavailable backend: {}", e.getMessage());
            }
        }

        if (best == null) {
            throw new IllegalStateException("No available render backend!");
        }

        LOGGER.info("Selected backend: {} (priority {})", best.getName(), best.getPriority());
        return best;
    }
}
