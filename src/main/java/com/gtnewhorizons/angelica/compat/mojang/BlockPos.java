package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3i;

import java.math.RoundingMode;

import static com.google.common.math.IntMath.log2;

// Should we keep this?
public class BlockPos extends Vector3i {

    private static final int SIZE_BITS_X = 1 + log2(MathHelper.roundUpToPowerOfTwo(30000000), RoundingMode.UNNECESSARY);
    private static final int SIZE_BITS_Z = SIZE_BITS_X;
    private static final int SIZE_BITS_Y = 64 - SIZE_BITS_X - SIZE_BITS_Z;
    private static final long BITS_X = (1L << SIZE_BITS_X) - 1L;
    private static final long BITS_Y = (1L << SIZE_BITS_Y) - 1L;
    private static final long BITS_Z = (1L << SIZE_BITS_Z) - 1L;
    private static final int BIT_SHIFT_Z = SIZE_BITS_Y;
    private static final int BIT_SHIFT_X =  SIZE_BITS_Y + SIZE_BITS_Z;

    public BlockPos() {
        super();
    }
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(ChunkPosition chunkPosition) {
        super(chunkPosition.chunkPosX, chunkPosition.chunkPosY, chunkPosition.chunkPosZ);
    }

    public int getX() {
        return this.x;
    }
    public int getY() {
        return this.y;
    }
    public int getZ() {
        return this.z;
    }

    public BlockPos set(int x, int y, int z) {
        super.set(x, y, z);
        return this;
    }

    /**
     * This method does NOT mutate the BlockPos
     */
    public BlockPos offset(ForgeDirection d) {
        return new BlockPos(this.x + d.offsetX, this.y + d.offsetY, this.z + d.offsetZ);
    }

    /**
     * This method does NOT mutate the BlockPos
     */
    public BlockPos down() {
        return offset(ForgeDirection.DOWN);
    }

    /**
     * This method does NOT mutate the BlockPos
     */
    public BlockPos up() {
        return offset(ForgeDirection.UP);
    }

    public long asLong() {
        return asLong(this.x, this.y, this.z);
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long)x & BITS_X) << BIT_SHIFT_X;
        l |= ((long)y & BITS_Y) << 0;
        l |= ((long)z & BITS_Z) << BIT_SHIFT_Z;
        return l;
    }

    public BlockPos of(long packedPos) {
        return set(unpackLongX(packedPos), unpackLongY(packedPos), unpackLongZ(packedPos));
    }

    public static int unpackLongX(long packedPos) {
        return (int)(packedPos << 64 - BIT_SHIFT_X - SIZE_BITS_X >> 64 - SIZE_BITS_X);
    }

    public static int unpackLongY(long packedPos) {
        return (int)(packedPos << 64 - SIZE_BITS_Y >> 64 - SIZE_BITS_Y);
    }

    public static int unpackLongZ(long packedPos) {
        return (int)(packedPos << 64 - BIT_SHIFT_Z - SIZE_BITS_Z >> 64 - SIZE_BITS_Z);
    }

    public static class Mutable extends BlockPos {

    }
}
