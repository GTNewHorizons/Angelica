package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

import java.math.RoundingMode;

import static com.google.common.math.IntMath.log2;

public interface BlockPos {

    int SIZE_BITS_X = 1 + log2(MathHelper.roundUpToPowerOfTwo(30000000), RoundingMode.UNNECESSARY);
    int SIZE_BITS_Z = SIZE_BITS_X;
    int SIZE_BITS_Y = 64 - SIZE_BITS_X - SIZE_BITS_Z;
    long BITS_X = (1L << SIZE_BITS_X) - 1L;
    long BITS_Y = (1L << SIZE_BITS_Y) - 1L;
    long BITS_Z = (1L << SIZE_BITS_Z) - 1L;
    int BIT_SHIFT_Z = SIZE_BITS_Y;
    int BIT_SHIFT_X =  SIZE_BITS_Y + SIZE_BITS_Z;

    int getX();
    int getY();
    int getZ();
    BlockPosImpl offset(ForgeDirection d);
    BlockPosImpl down();
    BlockPosImpl up();
    long asLong();

    static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & BITS_X) << BIT_SHIFT_X;
        l |= ((long) y & BITS_Y) << 0;
        l |= ((long) z & BITS_Z) << BIT_SHIFT_Z;
        return l;
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
}
