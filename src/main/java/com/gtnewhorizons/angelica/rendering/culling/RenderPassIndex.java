package com.gtnewhorizons.angelica.rendering.culling;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/** Stable index assignment for TerrainRenderPass instances. Keyed by pass name. */
public final class RenderPassIndex {
    public static final int MAX_PASSES = 8;

    private static final Object2IntOpenHashMap<String> INDICES = new Object2IntOpenHashMap<>(MAX_PASSES);
    static { INDICES.defaultReturnValue(-1); }

    private static int nextIndex;

    private RenderPassIndex() {}

    public static synchronized int indexOf(TerrainRenderPass pass) {
        final String name = pass.name();
        final int existing = INDICES.getInt(name);
        if (existing >= 0) return existing;
        if (nextIndex >= MAX_PASSES) {
            throw new IllegalStateException("RenderPassIndex: more than " + MAX_PASSES + " distinct TerrainRenderPass names; bump MAX_PASSES and the pass-bit budget in SectionMetaBuffer.sectionKey. Registered: " + INDICES);
        }
        final int assigned = nextIndex++;
        INDICES.put(name, assigned);
        return assigned;
    }
}
