package jss.notfine.util;

import net.minecraft.world.IBlockAccess;

public interface IFaceObstructionCheckHelper {

    boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ);

}
