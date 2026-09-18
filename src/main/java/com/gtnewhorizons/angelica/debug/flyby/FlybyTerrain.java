package com.gtnewhorizons.angelica.debug.flyby;

import java.util.function.IntBinaryOperator;

final class FlybyTerrain {

    static final int CORRIDOR_RADIUS = 2;
    static final int HOVER_BLOCKS = 2;

    private FlybyTerrain() {}

    static int routeFloor(double[] pathX, double[] pathZ, int radius, IntBinaryOperator heights) {
        int floor = Integer.MIN_VALUE;
        int prevCellX = 0;
        int prevCellZ = 0;
        for (int i = 0; i < pathX.length; i++) {
            final int cellX = (int) Math.floor(pathX[i]);
            final int cellZ = (int) Math.floor(pathZ[i]);
            if (i > 0 && cellX == prevCellX && cellZ == prevCellZ) continue;
            prevCellX = cellX;
            prevCellZ = cellZ;
            for (int x = cellX - radius; x <= cellX + radius; x++) {
                for (int z = cellZ - radius; z <= cellZ + radius; z++) {
                    floor = Math.max(floor, heights.applyAsInt(x, z));
                }
            }
        }
        return floor;
    }
}
