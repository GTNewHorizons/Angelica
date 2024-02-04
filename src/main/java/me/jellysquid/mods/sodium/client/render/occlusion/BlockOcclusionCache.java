package me.jellysquid.mods.sodium.client.render.occlusion;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockOcclusionCache {
    private static final byte UNCACHED_VALUE = (byte) 127;

    private final Object2ByteLinkedOpenHashMap<CachedOcclusionShapeTest> map;
    private final CachedOcclusionShapeTest cachedTest = new CachedOcclusionShapeTest();
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
    public boolean shouldDrawSide(Block block, int meta, IBlockAccess view, BlockPos pos, ForgeDirection facing) {
        if (facing == ForgeDirection.UNKNOWN)
            return true;

        BlockPos adjPos = this.cpos;
        adjPos.set(pos.getX() + facing.offsetX, pos.getY() + facing.offsetY, pos.getZ() + facing.offsetZ);

        Block adjState = view.getBlock(adjPos.x, adjPos.y, adjPos.z);

        return !adjState.isOpaqueCube();

        // TODO: Use VoxelShape occlusion from modern
    }

    private boolean calculate(Block selfShape, Block adjShape) {
        CachedOcclusionShapeTest cache = this.cachedTest;
        cache.a = selfShape;
        cache.b = adjShape;
        cache.updateHash();

        byte cached = this.map.getByte(cache);

        if (cached != UNCACHED_VALUE) {
            return cached == 1;
        }

        boolean ret = adjShape.isOpaqueCube() && selfShape.isOpaqueCube();

        this.map.put(cache.copy(), (byte) (ret ? 1 : 0));

        if (this.map.size() > 2048) {
            this.map.removeFirstByte();
        }

        return ret;
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

        public void updateHash() {
            int result = System.identityHashCode(this.a);
            result = 31 * result + System.identityHashCode(this.b);

            this.hashCode = result;
        }

        public CachedOcclusionShapeTest copy() {
            return new CachedOcclusionShapeTest(this.a, this.b, this.hashCode);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CachedOcclusionShapeTest) {
                CachedOcclusionShapeTest that = (CachedOcclusionShapeTest) o;

                return this.a == that.a &&
                    this.b == that.b;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
