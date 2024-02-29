package me.jellysquid.mods.sodium.client.model.light.flat;

import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Arrays;

/**
 * A light pipeline which implements "classic-style" lighting through simply using the light value of the adjacent
 * block to a face.
 */
public class FlatLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    public FlatLightPipeline(LightDataAccess lightCache) {
        this.lightCache = lightCache;
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPosImpl pos, QuadLightData out, ForgeDirection cullFace, ForgeDirection face, boolean shade) {
        int lightmap;

        // To match vanilla behavior, use the cull face if it exists/is available
        if (cullFace != null) {
            lightmap = getOffsetLightmap(pos, cullFace);
        } else {
            int flags = quad.getFlags();
            // If the face is aligned, use the light data above it
            // To match vanilla behavior, also treat the face as aligned if it is parallel and the block state is a full cube
            if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(pos)))) {
                lightmap = getOffsetLightmap(pos, face);
            } else {
                lightmap = LightDataAccess.unpackLM(this.lightCache.get(pos));
            }
        }

        Arrays.fill(out.lm, lightmap);
        final float brightness = (AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading())
            ? 1.0F : this.lightCache.getWorld().getBrightness(face, shade);
        Arrays.fill(out.br, brightness);
    }

    /**
     * When vanilla computes an offset lightmap with flat lighting, it passes the original BlockState but the
     * offset BlockPos to WorldRenderer#getLightmapCoordinates(BlockRenderView, BlockState, BlockPos)
     * This does not make much sense but fixes certain issues, primarily dark quads on light-emitting blocks
     * behind tinted glass. {@link LightDataAccess} cannot efficiently store lightmaps computed with
     * inconsistent values so this method exists to mirror vanilla behavior as closely as possible.
     */
    private int getOffsetLightmap(BlockPosImpl pos, ForgeDirection face) {
        int lightmap = LightDataAccess.unpackLM(this.lightCache.get(pos, face));
        // If the block light is not 15 (max)...
        if ((lightmap & 0xF0) != 0xF0) {
            int originLightmap = LightDataAccess.unpackLM(this.lightCache.get(pos));
            // ...take the maximum combined block light at the origin and offset positions
            lightmap = (lightmap & ~0xFF) | Math.max(lightmap & 0xFF, originLightmap & 0xFF);
        }
        return lightmap;
    }
}
