package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

public final class DebugRenderers {

    private static DebugWorldRenderHandler handler;
    private static boolean listening;

    private DebugRenderers() {}

    static DebugWorldRenderer create(DebugEntryDef def) {
        return switch (def.kind) {
            case DebugScreenEntries.KIND_CHUNK_BORDERS -> new ChunkBordersRenderer();
            case DebugScreenEntries.KIND_CHUNK_SECTION_VISIBILITY -> new SectionVisibilityRenderer();
            case DebugScreenEntries.KIND_VISUALIZE_BLOCK_LIGHT_LEVELS -> new LightLevelRenderer(false);
            case DebugScreenEntries.KIND_VISUALIZE_SKY_LIGHT_LEVELS -> new LightLevelRenderer(true);
            case DebugScreenEntries.KIND_VISUALIZE_HEIGHTMAP -> new HeightmapRenderer();
            case DebugScreenEntries.KIND_VISUALIZE_COLLISION_BOXES -> new CollisionBoxRenderer();
            case DebugScreenEntries.KIND_VISUALIZE_SOLID_FACES -> new SolidFaceRenderer();
            case DebugScreenEntries.KIND_VISUALIZE_WATER_LEVELS -> new WaterLevelRenderer();
            case DebugScreenEntries.KIND_VISUALIZE_ENTITY_SUPPORTING_BLOCK -> new SupportBlockRenderer();
            default -> throw new IllegalArgumentException(def.id);
        };
    }

    static boolean isWorldRenderer(DebugEntryDef def) {
        return def.kind >= DebugScreenEntries.KIND_CHUNK_BORDERS && def.kind <= DebugScreenEntries.KIND_VISUALIZE_ENTITY_SUPPORTING_BLOCK;
    }

    public static void onEnabledChanged() {
        final DebugRegistry registry = DebugRegistry.MAIN;
        final int n = registry.rendererCount();
        if (handler == null) {
            if (!anyEnabled(registry, n)) return;
            handler = new DebugWorldRenderHandler(registry);
            MinecraftForge.EVENT_BUS.register(handler);
        }
        final DebugRenderSlots slots = handler.slots();
        for (int i = 0; i < n; i++) {
            final DebugEntryDef def = registry.rendererAt(i);
            if (isWorldRenderer(def)) slots.setEnabled(i, def.isEnabled());
        }
        final boolean any = slots.enabledCount() > 0;
        if (!any) handler.processReleases();
        if (any == listening) return;
        listening = any;
        final World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        if (any) {
            world.addWorldAccess(slots.changes);
        } else {
            world.removeWorldAccess(slots.changes);
        }
    }

    private static boolean anyEnabled(DebugRegistry registry, int n) {
        for (int i = 0; i < n; i++) {
            final DebugEntryDef def = registry.rendererAt(i);
            if (isWorldRenderer(def) && def.isEnabled()) return true;
        }
        return false;
    }
}
