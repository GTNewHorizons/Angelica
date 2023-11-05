package me.jellysquid.mods.sodium.client.model.quad.blender;

import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.client.Minecraft;

class ConfigurableColorBlender implements BiomeColorBlender {
    private final BiomeColorBlender defaultBlender;
    private final BiomeColorBlender smoothBlender;

    public ConfigurableColorBlender(Minecraft client) {
        this.defaultBlender = new FlatBiomeColorBlender();
        this.smoothBlender = isSmoothBlendingEnabled(client) ? new SmoothBiomeColorBlender() : this.defaultBlender;
    }

    private static boolean isSmoothBlendingEnabled(Minecraft client) {
        return client.options.biomeBlendRadius > 0;
    }

    @Override
    public int[] getColors(BlockColorProvider colorizer, BlockRenderView world, BlockState state, BlockPos origin,
			ModelQuadView quad) {
    	BiomeColorBlender blender;

        if (BlockColorSettings.isSmoothBlendingEnabled(world, state, origin)) {
            blender = this.smoothBlender;
        } else {
            blender = this.defaultBlender;
        }

        return blender.getColors(colorizer, world, state, origin, quad);
    }

}
