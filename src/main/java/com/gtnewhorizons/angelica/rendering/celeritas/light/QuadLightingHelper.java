package com.gtnewhorizons.angelica.rendering.celeritas.light;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

public final class QuadLightingHelper {
    private QuadLightingHelper() {}

    // Known emissive brightness values set by mods/vanilla
    public static final int FULL_BRIGHT_15 = (15 << 20) | (15 << 4); // 15728880
    public static final int FULL_BRIGHT_14 = (14 << 20) | (14 << 4); // 14680288

    public static boolean isBlockEmissive(IBlockAccess world, int x, int y, int z) {
        final Block block = world.getBlock(x, y, z);
        try {
            return block.getMixedBrightnessForBlock(EmptyBlockAccess.INSTANCE, x, y, z) == FULL_BRIGHT_15;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isQuadFullBright(ChunkVertexEncoder.Vertex[] vertices) {
        final int first = vertices[0].light;

        // Must be a known emissive brightness value
        if (first != FULL_BRIGHT_15 && first != FULL_BRIGHT_14) {
            return false;
        }

        // All 4 vertices must have the same value
        for (int i = 1; i < 4; i++) {
            if (vertices[i].light != first) {
                return false;
            }
        }
        return true;
    }
}
