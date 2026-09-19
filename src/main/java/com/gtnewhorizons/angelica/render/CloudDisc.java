package com.gtnewhorizons.angelica.render;

/**
 * The shape of the drawn disc, how it splits into wedges, and how far out each kind of geometry should
 * really be worth building.
 */
final class CloudDisc {

    static final int CELLS_PER_CHUNK = 8;
    static final int MARGIN_CELLS = 16;
    static final int WEDGE_COUNT = 32;
    static final double WEDGES_PER_RADIAN = WEDGE_COUNT / (2.0 * Math.PI);
    static final int ALWAYS_DRAWN_CELLS = Math.max(CELLS_PER_CHUNK, 4 * MARGIN_CELLS);
    static final float SCROLL_SPEED = 1.0f / 256.0f;
    private static final double WALL_CUT_PIXELS = 0.625;
    private static final double PLATE_LOD_PIXELS = 0.35;
    private static final double TAN_11_25_DEG = 0.19891236737965800; // √(4+2√2)−√2−1
    private static final double TAN_22_5_DEG  = 0.41421356237309503; // √2−1
    private static final double TAN_33_75_DEG = 0.66817863791929892; // √(4−2√2)+1−√2
    private static final int DISTANCE_STEP_CELLS = 32;

    static {
        if (WEDGE_COUNT != 32) {
            throw new AssertionError("wedgeOf splits four quadrants into eight; WEDGE_COUNT must be 32");
        }
    }

    private CloudDisc() {
    }

    static double pixelsPerRadian(int displayHeight, float fovDegrees) {
        if (!(fovDegrees > 0.0f) || fovDegrees >= 180.0f) return 0.0;
        return (displayHeight * 0.5) / Math.tan(Math.toRadians(fovDegrees) * 0.5);
    }

    static int wallCutCells(double pixelsPerRadian, boolean platesInFront) {
        if (!platesInFront || pixelsPerRadian <= 0.0) return Integer.MAX_VALUE;

        return roundUpToStep(pixelsPerRadian / (3.0 * WALL_CUT_PIXELS));
    }

    static int plateLodCells(double pixelsPerRadian, float cloudBaseRelativeY, float cellWidthBlocks, boolean coarseCoverUsable) {
        if (!coarseCoverUsable || pixelsPerRadian <= 0.0) return Integer.MAX_VALUE;

        final double heightBlocks = Math.max(1.0, Math.abs(cloudBaseRelativeY));
        return roundUpToStep(Math.sqrt(heightBlocks * pixelsPerRadian / (cellWidthBlocks * PLATE_LOD_PIXELS)));
    }

    private static int roundUpToStep(double cells) {
        final long steps = (long) (cells / DISTANCE_STEP_CELLS) + 1;
        return (int) Math.min(steps * DISTANCE_STEP_CELLS, Integer.MAX_VALUE);
    }

    static int wedgeOf(int x, int z) {
        int foldedX = -x;
        int foldedZ = -z;
        if (foldedX == 0 && foldedZ == 0) return WEDGE_COUNT / 2;

        final int quadrant;
        if (foldedX > 0 && foldedZ >= 0) {
            quadrant = 0;
        } else if (foldedX <= 0 && foldedZ > 0) {
            quadrant = 1;
            final int oldX = foldedX;
            foldedX = foldedZ;
            foldedZ = -oldX;
        } else if (foldedX < 0) {
            quadrant = 2;
            foldedX = -foldedX;
            foldedZ = -foldedZ;
        } else {
            quadrant = 3;
            final int oldX = foldedX;
            foldedX = -foldedZ;
            foldedZ = oldX;
        }

        final int withinQuadrant;
        if (foldedZ < foldedX) {
            if (foldedZ < TAN_11_25_DEG * foldedX) withinQuadrant = 0;
            else if (foldedZ < TAN_22_5_DEG * foldedX) withinQuadrant = 1;
            else if (foldedZ < TAN_33_75_DEG * foldedX) withinQuadrant = 2;
            else withinQuadrant = 3;
        } else {
            if (foldedX > TAN_33_75_DEG * foldedZ) withinQuadrant = 4;
            else if (foldedX > TAN_22_5_DEG * foldedZ) withinQuadrant = 5;
            else if (foldedX > TAN_11_25_DEG * foldedZ) withinQuadrant = 6;
            else withinQuadrant = 7;
        }

        return quadrant * 8 + withinQuadrant;
    }

    static double driftAngle(double driftCells) {
        final double nearestCulledCells = ALWAYS_DRAWN_CELLS - driftCells;
        return nearestCulledCells <= driftCells ? Math.PI / 2.0 : Math.asin(driftCells / nearestCulledCells);
    }
}
