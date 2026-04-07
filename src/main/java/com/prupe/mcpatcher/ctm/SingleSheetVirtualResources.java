package com.prupe.mcpatcher.ctm;

import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SingleSheetVirtualResources {

    private static final Map<String, BufferedImage> store = new ConcurrentHashMap<>();

    private SingleSheetVirtualResources() {}

    public static void register(ResourceLocation location, BufferedImage image) {
        store.put(toKey(location), image);
    }

    public static BufferedImage get(ResourceLocation location) {
        return store.get(toKey(location));
    }

    public static boolean has(ResourceLocation location) {
        return store.containsKey(toKey(location));
    }

    public static void clear() {
        store.clear();
    }

    private static String toKey(ResourceLocation location) {
        return location.getResourceDomain() + ':' + location.getResourcePath();
    }
}
