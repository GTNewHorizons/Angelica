package me.jellysquid.mods.sodium.client.render.occlusion;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

public class BlockOcclusionCache {
    private static final byte UNCACHED_VALUE = (byte) 127;

    private final Object2ByteLinkedOpenHashMap<CachedOcclusionShapeTest> map;
    private final BlockPos cpos = new BlockPos();

    public BlockOcclusionCache() {
        this.map = new Object2ByteLinkedOpenHashMap<>(2048, 0.5F);
        this.map.defaultReturnValue(UNCACHED_VALUE);
    }

    /**
     * @param block The block in the world
     * @param meta The meta value of the block
     * @param view The world view for this render context
     * @param pos The position of the block
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(Block block, int meta, IBlockAccess view, BlockPos pos, ModelQuadFacing facing) {
        if (facing == ModelQuadFacing.UNASSIGNED)
            return true;

        final BlockPos adjPos = this.cpos;
        adjPos.set(pos.getX() + facing.getStepX(), pos.getY() + facing.getStepY(), pos.getZ() + facing.getStepZ());

        final Block adjState = view.getBlock(adjPos.x, adjPos.y, adjPos.z);

        return !adjState.isOpaqueCube();

        // TODO: Use VoxelShape occlusion from modern
    }

    private static final class CachedOcclusionShapeTest {
        private Block a, b;
        private int hashCode;

        private CachedOcclusionShapeTest() {

        }

        private CachedOcclusionShapeTest(Block a, Block b, int hashCode) {
            this.a = a;
            this.b = b;
            this.hashCode = hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CachedOcclusionShapeTest that) {
                return this.a == that.a && this.b == that.b;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
