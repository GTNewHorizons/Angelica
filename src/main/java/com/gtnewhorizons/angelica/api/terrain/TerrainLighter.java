package com.gtnewhorizons.angelica.api.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.VertexArrayQuadView;
import com.gtnewhorizons.angelica.rendering.celeritas.light.LightDataCache;
import com.gtnewhorizons.angelica.rendering.celeritas.light.VanillaDiffuseProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.light.flat.FlatLightPipeline;
import org.embeddedt.embeddium.impl.model.light.smooth.SmoothLightPipeline;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;

/**
 * Per vertex AO and lightmap for block quads, using the chunk mesher's light pipelines over any block access.
 * <p>
 * Not thread safe, use one per thread. Light data is cached per 16 block region and doesn't see world changes, so call
 * {@link #reset} before each bake.
 */
public final class TerrainLighter {

    private final LightDataCache cache = new LightDataCache();
    private final SmoothLightPipeline smooth;
    private final FlatLightPipeline flat;
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final VertexArrayQuadView quad = new VertexArrayQuadView(vertices);
    private final QuadLightData out = new QuadLightData();
    private int regionX = Integer.MIN_VALUE, regionY, regionZ;
    // Last fluid quad, to detect the flipped back face vanilla emits right after it
    private ModelQuadFacing fluidLightFace;
    private final float[] fluidXyz = new float[12];
    private static final int[] REVERSED = { 0, 3, 2, 1 };

    public TerrainLighter(IBlockAccess world) {
        cache.setWorld(world);
        smooth = new SmoothLightPipeline(cache, VanillaDiffuseProvider.INSTANCE, true);
        flat = new FlatLightPipeline(cache, VanillaDiffuseProvider.INSTANCE, true);
    }

    /** Drops cached light data */
    public void reset() {
        regionX = Integer.MIN_VALUE;
        fluidLightFace = null;
    }

    /**
     * Lights one quad of the block at x, y, z.
     *
     * @param xyz        corners as x y z in vertex order, in the block access's coordinates
     * @param shade      multiply in directional face shading, false for AO only
     * @param fluid      the block is a fluid (see {@link TerrainRendering#isFluid}). Pass its quads in emit order. The
     *                   mesher gives fluids no AO, so only use the light
     * @param brightness out, per vertex AO (times shading if requested), 0 to 1
     * @param light      out, per vertex lightmap, {@code sky << 16 | block}, each 0-240
     */
    public void light(float[] xyz, int x, int y, int z, boolean shade, boolean fluid, float[] brightness,
        int[] light) {
        final int rx = x & ~15, ry = y & ~15, rz = z & ~15;
        if (rx != regionX || ry != regionY || rz != regionZ) {
            regionX = rx;
            regionY = ry;
            regionZ = rz;
            cache.reset(rx, ry, rz);
        }
        smooth.reset();
        flat.reset();
        for (int i = 0; i < 4; i++) {
            vertices[i].x = xyz[i * 3];
            vertices[i].y = xyz[i * 3 + 1];
            vertices[i].z = xyz[i * 3 + 2];
        }
        // The mesher lights a fluid's flipped back face from the front face's side
        final boolean flippedCopy = fluid && fluidLightFace != null && isReversedFluidQuad(xyz);
        quad.setup(QuadUtil.calculateNormal(vertices), x, y, z, fluid ? ModelQuadFlags.IS_VANILLA_SHADED : 0,
            flippedCopy ? fluidLightFace : null);
        // Same rule as the mesher: smooth lighting on and the block isn't a light source
        final boolean smoothLighting = Minecraft.getMinecraft().gameSettings.ambientOcclusion != 0
            && LightDataAccess.unpackLU(cache.get(x, y, z)) == 0;
        final LightPipeline pipeline = smoothLighting ? smooth : flat;
        pipeline.calculate(quad, x, y, z, out, quad.getCullFace(), quad.getLightFace(), shade, true);
        System.arraycopy(out.br, 0, brightness, 0, 4);
        System.arraycopy(out.lm, 0, light, 0, 4);
        if (fluid && !flippedCopy) {
            fluidLightFace = quad.getLightFace();
            System.arraycopy(xyz, 0, fluidXyz, 0, 12);
        } else if (!fluid) {
            fluidLightFace = null;
        }
    }

    private boolean isReversedFluidQuad(float[] xyz) {
        for (int i = 0; i < 4; i++) {
            final int src = REVERSED[i] * 3;
            if (xyz[i * 3] != fluidXyz[src] || xyz[i * 3 + 1] != fluidXyz[src + 1]
                || xyz[i * 3 + 2] != fluidXyz[src + 2]) {
                return false;
            }
        }
        return true;
    }
}
