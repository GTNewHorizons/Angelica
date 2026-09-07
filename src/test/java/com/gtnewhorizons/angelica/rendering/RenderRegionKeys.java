package com.gtnewhorizons.angelica.rendering;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;

import java.lang.reflect.Constructor;

public final class RenderRegionKeys {

    private static final int COMMON_VERTEX_STRIDE = ChunkMeshFormats.VANILLA_LIKE.getVertexFormat().getStride();

    private RenderRegionKeys() {
    }

    public static RenderRegion create(int x, int y, int z, int id) {
        try {
            for (Constructor<?> ctor : RenderRegion.class.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 6) {
                    ctor.setAccessible(true);
                    return (RenderRegion) ctor.newInstance(x, y, z, id, null, COMMON_VERTEX_STRIDE);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("RenderRegion constructor shape changed");
    }
}
