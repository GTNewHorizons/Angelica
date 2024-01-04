package com.prupe.mcpatcher.mal.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.IResourcePack;

import com.prupe.mcpatcher.MCLogger;

abstract public class TexturePackChangeHandler implements Comparable<TexturePackChangeHandler> {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.TEXTURE_PACK);

    private static final ArrayList<TexturePackChangeHandler> handlers = new ArrayList<>();
    private static int recurseDepth;
    private static boolean initialized;
    private static long startTime;
    private static long startMem;

    private boolean updateNeeded;

    protected final String name;
    protected final int order;

    public TexturePackChangeHandler(String name, int order) {
        this.name = name;
        this.order = order;
    }

    public void initialize() {
        beforeChange();
        afterChange();
    }

    public void refresh() {
        beforeChange();
        afterChange();
    }

    abstract public void beforeChange();

    abstract public void afterChange();

    public void afterChange2() {}

    @Override
    public int compareTo(TexturePackChangeHandler that) {
        return this.order - that.order;
    }

    public static void register(TexturePackChangeHandler handler) {
        if (handler != null) {
            try {
                logger.info("initializing %s...", handler.name);
                handler.initialize();
            } catch (Throwable e) {
                e.printStackTrace();
                logger.severe("%s initialization failed", handler.name);
            }
            handlers.add(handler);
            logger.fine("registered texture pack handler %s, priority %d", handler.name, handler.order);
            Collections.sort(handlers);
        }
    }

    public static void checkForTexturePackChange() {
        for (TexturePackChangeHandler handler : handlers) {
            if (handler.updateNeeded) {
                handler.updateNeeded = false;
                try {
                    logger.info("refreshing %s...", handler.name);
                    handler.refresh();
                } catch (Throwable e) {
                    e.printStackTrace();
                    logger.severe("%s refresh failed", handler.name);
                }
            }
        }
    }

    public static void beforeChange1() {
        logger.finer("beforeChange1 depth %d", recurseDepth);
        if (recurseDepth++ > 0) {
            return;
        }
        startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        startMem = runtime.totalMemory() - runtime.freeMemory();
        ResourceList.clearInstance();
        List<IResourcePack> resourcePacks = TexturePackAPI.getResourcePacks(null);
        logger
            .fine("%s resource packs (%d selected):", initialized ? "changing" : "initializing", resourcePacks.size());
        for (IResourcePack pack : resourcePacks) {
            logger.fine("resource pack: %s", pack.getPackName());
        }
        Set<String> namespaces = TexturePackAPI.getNamespaces();
        logger.fine("%d resource namespaces:", namespaces.size());
        for (String namespace : namespaces) {
            logger.fine("namespace: %s", namespace);
        }

        for (TexturePackChangeHandler handler : handlers) {
            try {
                logger.info("refreshing %s (pre)...", handler.name);
                handler.beforeChange();
            } catch (Throwable e) {
                e.printStackTrace();
                logger.severe("%s.beforeChange failed", handler.name);
            }
        }

        TexturePackAPI.flushUnusedTextures();
    }

    public static void afterChange1() {
        logger.finer("afterChange1 depth %d", recurseDepth - 1);
        if (--recurseDepth > 0) {
            return;
        }
        for (TexturePackChangeHandler handler : handlers) {
            try {
                logger.info("refreshing %s (post)...", handler.name);
                handler.afterChange();
            } catch (Throwable e) {
                e.printStackTrace();
                logger.severe("%s.afterChange failed", handler.name);
            }
        }

        for (int i = handlers.size() - 1; i >= 0; i--) {
            TexturePackChangeHandler handler = handlers.get(i);
            try {
                handler.afterChange2();
            } catch (Throwable e) {
                e.printStackTrace();
                logger.severe("%s.afterChange2 failed", handler.name);
            }
        }

        System.gc();
        long timeDiff = System.currentTimeMillis() - startTime;
        Runtime runtime = Runtime.getRuntime();
        long memDiff = runtime.totalMemory() - runtime.freeMemory() - startMem;
        logger.info("done (%.3fs elapsed, mem usage %+.1fMB)\n", timeDiff / 1000.0, memDiff / 1048576.0);
        initialized = true;
        recurseDepth = 0;
    }
}
