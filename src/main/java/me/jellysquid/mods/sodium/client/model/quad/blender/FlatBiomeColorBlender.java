package me.jellysquid.mods.sodium.client.model.quad.blender;

import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;

/**
 * A simple colorizer which performs no blending between adjacent blocks.
 */
public class FlatBiomeColorBlender implements BiomeColorBlender {
    private final int[] cachedRet = new int[4];

    @Override
    public int[] getColors(BlockColorProvider colorizer, BlockRenderView world, BlockState state, BlockPos origin,
                           ModelQuadView quad) {
        // TODO: Sodium - Colorizer
//        Arrays.fill(this.cachedRet, ColorARGB.toABGR(colorizer.getColor(state, world, origin, quad.getColorIndex())));

        return this.cachedRet;
    }
}
