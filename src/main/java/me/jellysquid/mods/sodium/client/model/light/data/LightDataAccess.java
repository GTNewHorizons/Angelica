package me.jellysquid.mods.sodium.client.model.light.data;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import me.jellysquid.mods.sodium.client.util.StateUtil;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.Block;

/**
 * The light data cache is used to make accessing the light data and occlusion properties of blocks cheaper. The data
 * for each block is stored as a long integer with packed fields in order to work around the lack of value types in Java.
 *
 * This code is not very pretty, but it does perform significantly faster than the vanilla implementation and has
 * good cache locality.
 *
 * Each long integer contains the following fields:
 * - OP: Block opacity test, true if opaque
 * - FO: Full block opaque test, true if opaque
 * - AO: Ambient occlusion, floating point value in the range of 0.0..1.0 encoded as an 12-bit unsigned integer
 * - LM: Light map texture coordinates, two packed UV shorts in an integer
 *
 * You can use the various static pack/unpack methods to extract these values in a usable format.
 */
public abstract class LightDataAccess {
    public static final ThreadLocal<BlockPos> DynamicLightsPos = ThreadLocal.withInitial(BlockPos::new);
    protected WorldSlice world;

    public long get(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2) {
        return this.get(x + d1.getStepX() + d2.getStepX(), y + d1.getStepY() + d2.getStepY(), z + d1.getStepZ() + d2.getStepZ());
    }

    public long get(int x, int y, int z, ModelQuadFacing dir) {
        return this.get(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
    }

    public long get(BlockPos pos, ModelQuadFacing dir) {
        return this.get(pos.x, pos.y, pos.z, dir);
    }

    public long get(BlockPos pos) {
        return this.get(pos.x, pos.y, pos.getZ());
    }

    /**
     * Returns the light data for the block at the given position. The property fields can then be accessed using
     * the various unpack methods below.
     */
    public abstract long get(int x, int y, int z);

    protected long compute(int x, int y, int z) {

        final WorldSlice world = this.world;

        final Block block = world.getBlock(x, y, z);

        final float ao;
        final boolean em;

        if (block.getLightValue() == 0) {
            ao = block.getAmbientOcclusionLightValue();
            em = false; //state.hasEmissiveLighting(world, pos);
        } else {
            ao = 1.0f;
            em = true;
        }

        // First is shouldBlockVision on modern
        final boolean op = !block.isOpaqueCube() || block.getLightOpacity() == 0;
        final boolean fo = block.isOpaqueCube();
        // Should be isFullCube, but this is probably close enough
        final boolean fc = StateUtil.areBoundsFullCube(block);

        // OPTIMIZE: Do not calculate lightmap data if the block is full and opaque.
        // FIX: Calculate lightmap data for light-emitting or emissive blocks, even though they are full and opaque.
        final int lm = (fo && !em) ? 0 : block.getMixedBrightnessForBlock(world, x, y, z);

        return packAO(ao) | packLM(lm) | packOP(op) | packFO(fo) | packFC(fc) | (1L << 60);
    }

    public static long packOP(boolean opaque) {
        return (opaque ? 1L : 0L) << 56;
    }

    public static boolean unpackOP(long word) {
        return ((word >>> 56) & 0b1) != 0;
    }

    public static long packFO(boolean opaque) {
        return (opaque ? 1L : 0L) << 57;
    }

    public static boolean unpackFO(long word) {
        return ((word >>> 57) & 0b1) != 0;
    }

    public static long packFC(boolean fullCube) {
        return (fullCube ? 1L : 0L) << 58;
    }

    public static boolean unpackFC(long word) {
        return ((word >>> 58) & 0b1) != 0;
    }

    public static long packLM(int lm) {
        return (long) lm & 0xFFFFFFFFL;
    }

    public static int unpackLM(long word) {
        return (int) (word & 0xFFFFFFFFL);
    }

    public static long packAO(float ao) {
        int aoi = (int) (ao * 4096.0f);
        return ((long) aoi & 0xFFFFL) << 32;
    }

    public static float unpackAO(long word) {
        int aoi = (int) (word >>> 32 & 0xFFFFL);
        return aoi * (1.0f / 4096.0f);
    }

    public static int getLightMap(long originWord) {
        if (!DynamicLights.isEnabled()) {
            return unpackLM(originWord);
        }
        if (unpackFO(originWord)){
            return unpackLM(originWord);
        }

        double dynamic = DynamicLights.get().getDynamicLightLevel(DynamicLightsPos.get());
        return DynamicLights.get().getLightmapWithDynamicLight(dynamic, unpackLM(originWord));

    }

    public WorldSlice getWorld() {
        return this.world;
    }
}
