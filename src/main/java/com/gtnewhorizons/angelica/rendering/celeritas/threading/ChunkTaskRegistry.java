package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChunkTaskRegistry {
    private static final Logger LOGGER = LogManager.getLogger("ChunkTaskRegistry");
    private static final List<ChunkTaskProvider> providers = new ArrayList<>();
    private static ChunkTaskProvider activeProvider;

    private ChunkTaskRegistry() {}

    public static synchronized void registerProvider(ChunkTaskProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(ChunkTaskProvider::priority));
        activeProvider = null;
        LOGGER.info("Registered chunk task provider: {} (priority={})",
            provider.getClass().getSimpleName(), provider.priority());
    }

    public static synchronized ChunkTaskProvider getActiveProvider() {
        for (ChunkTaskProvider provider : providers) {
            if (provider.isEnabled()) {
                if (activeProvider != provider) {
                    activeProvider = provider;
                    LOGGER.info("Active chunk task provider: {} (threads={})", activeProvider.getClass().getSimpleName(), activeProvider.threadCount());
                }
                return activeProvider;
            }
        }

        if (activeProvider != DefaultChunkTaskProvider.INSTANCE) {
            activeProvider = DefaultChunkTaskProvider.INSTANCE;
            LOGGER.info("Active chunk task provider: single-threaded fallback");
        }
        return activeProvider;
    }

    public static synchronized void reset() {
        providers.clear();
        activeProvider = null;
    }
}
