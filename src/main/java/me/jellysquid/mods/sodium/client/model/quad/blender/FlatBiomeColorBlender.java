package me.jellysquid.mods.sodium.client.model.quad.blender;

import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.util.color.ColorARGB;

import java.util.Arrays;

/**
 * A simple colorizer which performs no blending between adjacent blocks.
 */
public class FlatBiomeColorBlender implements BiomeColorBlender {
    private final int[] cachedRet = new int[4];

    @Override
    public int[] getColors(BlockColorProvider colorizer, BlockRenderView world, BlockState state, BlockPos origin,
                           ModelQuadView quad) {
        Arrays.fill(this.cachedRet, ColorARGB.toABGR(colorizer.getColor(state, world, origin, quad.getColorIndex())));

        return this.cachedRet;
    }
}
