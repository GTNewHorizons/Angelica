package me.jellysquid.mods.sodium.client.model.light;

import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Light pipelines allow model quads for any location in the world to be lit using various backends, including fluids
 * and block entities.
 */
public interface LightPipeline {
    /**
     * Calculates the light data for a given block model quad, storing the result in {@param out}.
     * @param quad The block model quad
     * @param pos The block position of the model this quad belongs to
     * @param out The data arrays which will store the calculated light data results
     * @param face The pre-computed facing vector of the quad
     * @param shade True if the block is shaded by ambient occlusion
     */
	void calculate(ModelQuadView quad, BlockPosImpl pos, QuadLightData out, ForgeDirection cullFace, ForgeDirection face, boolean shade);
}
