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
 * Per-vertex AO and lightmap for block quads, worked out like the chunk mesher lights terrain (same light pipeline and
 * light data) but over any block access. For mods baking block geometry outside the chunk renderer.
 * <p>
 * Not thread safe, use one per thread. Light data is cached per 16 block region, so quads from the same region in a
 * row are cheapest. The cache doesn't notice world changes, call {@link #reset} before each bake.
 */
public final class TerrainLighter {

    private final LightDataCache cache = new LightDataCache();
    private final SmoothLightPipeline smooth;
    private final FlatLightPipeline flat;
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final VertexArrayQuadView quad = new VertexArrayQuadView(vertices);
    private final QuadLightData out = new QuadLightData();
    private int regionX = Integer.MIN_VALUE, regionY, regionZ;
    // The last fluid quad, to spot the back face vanilla draws straight after it
    private ModelQuadFacing fluidLightFace;
    private final float[] fluidXyz = new float[12];
    private static final int[] REVERSED = { 0, 3, 2, 1 };

    public TerrainLighter(IBlockAccess world) {
        cache.setWorld(world);
        smooth = new SmoothLightPipeline(cache, VanillaDiffuseProvider.INSTANCE, true);
        flat = new FlatLightPipeline(cache, VanillaDiffuseProvider.INSTANCE, true);
    }

    /** Drops the cached light data so the next quad reads the world again. Call it before each bake. */
    public void reset() {
        regionX = Integer.MIN_VALUE;
        fluidLightFace = null;
    }

    /**
     * Lights one quad of the block at x, y, z.
     *
     * @param xyz        the four corners as x y z, in the block access's coordinates and the quad's vertex order
     * @param shade      also multiply in the face's directional shading, like terrain unless the pack turns it off.
     *                   False gives AO alone
     * @param fluid      the block is a fluid (see {@code TerrainRendering.isFluid}). Lit like the mesher lights fluids,
     *                   where the back face vanilla draws right after each face gets its light from the same side as
     *                   the front. Pass a block's quads in the order they were made. Only use the light for fluids,
     *                   the mesher gives them no AO
     * @param brightness out, per vertex AO (times shading if asked), 0 to 1
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
        final boolean flippedCopy = fluid && fluidLightFace != null && isReversedFluidQuad(xyz);
        quad.setup(QuadUtil.calculateNormal(vertices), x, y, z, fluid ? ModelQuadFlags.IS_VANILLA_SHADED : 0,
            flippedCopy ? fluidLightFace : null);
        // Same rule as the mesher, smooth lighting on and the block doesn't give off light
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
