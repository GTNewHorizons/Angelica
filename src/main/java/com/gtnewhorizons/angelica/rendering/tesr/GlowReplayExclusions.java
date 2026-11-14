package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public final class GlowReplayExclusions {

    private static final ReferenceOpenHashSet<Class<?>> registered = new ReferenceOpenHashSet<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, Boolean> excluded = new Reference2ObjectOpenHashMap<>();

    private GlowReplayExclusions() {}

    public static void exclude(Class<?> type) {
        if (registered.add(type)) excluded.clear();
    }

    public static boolean excludes(TileEntity tileEntity) {
        final Class<? extends TileEntity> type = tileEntity.getClass();
        Boolean result = excluded.get(type);
        if (result == null) {
            final TileEntitySpecialRenderer renderer = TileEntityRendererDispatcher.instance.getSpecialRendererByClass(type);
            result = matches(type) || renderer != null && matches(renderer.getClass());
            excluded.put(type, result);
        }
        return result;
    }

    private static boolean matches(Class<?> type) {
        final String[] names = AngelicaConfig.glowDepthReplayExcludedClasses;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (registered.contains(c)) return true;
            if (names != null) for (String name : names) {
                if (c.getName().equals(name)) return true;
            }
        }
        return false;
    }
}
