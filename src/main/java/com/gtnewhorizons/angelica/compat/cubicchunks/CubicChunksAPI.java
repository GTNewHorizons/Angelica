package com.gtnewhorizons.angelica.compat.cubicchunks;

import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;

public class CubicChunksAPI {

    public static ExtendedBlockStorage getCubeStorage(World world, int x, int y, int z) {
        Cube cube = ((ICubeProviderInternal) world.getChunkProvider()).getLoadedCube(x, y, z);

        if (cube == null || cube.isEmpty()) return null;

        return cube.getStorage();
    }

    public static int[] getLoadedCubeLevelsInColumn(World world, int chunkX, int chunkZ) {
        IColumn column = (IColumn) world.getChunkFromChunkCoords(chunkX, chunkZ);

        return column.getLoadedCubes().stream().mapToInt(ICube::getY).toArray();
    }
}
