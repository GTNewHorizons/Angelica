package com.gtnewhorizons.angelica.render;

import java.util.Arrays;

import static com.gtnewhorizons.angelica.render.CloudDisc.ALWAYS_DRAWN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGE_COUNT;
import static com.gtnewhorizons.angelica.render.CloudDisc.wedgeOf;

/**
 * The plate rectangles that land inside the disc for one anchor.
 */
final class CloudPlateCover {

    private final int[] wedgeBucketCount = new int[WEDGE_COUNT + 2];
    private int[] rects = new int[4096 * 4];
    private int[] rectWedgeBucket = new int[4096];
    private int[] orderedRects = new int[4096];
    private int count;
    private boolean groupByWedge = true;

    int count() {
        return count;
    }

    int orderedRect(int order) {
        return orderedRects[order];
    }

    int minX(int rect) {
        return rects[rect * 4];
    }

    int minZ(int rect) {
        return rects[rect * 4 + 1];
    }

    int maxX(int rect) {
        return rects[rect * 4 + 2];
    }

    int maxZ(int rect) {
        return rects[rect * 4 + 3];
    }

    int wedgeBucket(int rect) {
        return rectWedgeBucket[rect];
    }

    void build(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq, int lodCells, boolean groupByWedge) {
        count = 0;
        this.groupByWedge = groupByWedge;
        Arrays.fill(wedgeBucketCount, 0);

        final int texW = shape.width;
        final int texH = shape.height;
        final int[] coarseRects = shape.coarsePlateRects;

        if (coarseRects == null || lodCells >= radiusCells) {
            stampRects(shape.plateRects, anchorX, anchorZ, texW, texH, radiusCells, radiusCellsSq, 0, 0, -1, -1, false);
        } else {
            final int fineX0 = 2 * Math.floorDiv(anchorX - lodCells, 2) - anchorX;
            final int fineZ0 = 2 * Math.floorDiv(anchorZ - lodCells, 2) - anchorZ;
            final int fineX1 = 2 * Math.floorDiv(anchorX + lodCells, 2) + 1 - anchorX;
            final int fineZ1 = 2 * Math.floorDiv(anchorZ + lodCells, 2) + 1 - anchorZ;
            stampRects(shape.plateRects, anchorX, anchorZ, texW, texH, radiusCells, radiusCellsSq, fineX0, fineZ0, fineX1, fineZ1, false);
            stampRects(coarseRects, anchorX, anchorZ, texW, texH, radiusCells, radiusCellsSq, fineX0, fineZ0, fineX1, fineZ1, true);
        }

        if (groupByWedge) orderByWedge();
    }

    private void stampRects(int[] sourceRects, int anchorX, int anchorZ, int texW, int texH, int radiusCells, int radiusCellsSq,
                            int fineX0, int fineZ0, int fineX1, int fineZ1, boolean outsideFineBox) {
        final int minCell = -radiusCells;
        final int maxCell = radiusCells;

        for (int r = 0; r < sourceRects.length; r += 4) {
            final int rectW = sourceRects[r + 2];
            final int rectH = sourceRects[r + 3];
            final int baseX = Math.floorMod(sourceRects[r] - anchorX, texW);
            final int baseZ = Math.floorMod(sourceRects[r + 1] - anchorZ, texH);

            for (int kz = Math.floorDiv(minCell - rectH + 1 - baseZ, texH); kz <= Math.floorDiv(maxCell - baseZ, texH); kz++) {
                final int cellZ0 = Math.max(baseZ + kz * texH, minCell);
                final int cellZ1 = Math.min(baseZ + kz * texH + rectH - 1, maxCell);
                if (cellZ0 > cellZ1) continue;

                for (int kx = Math.floorDiv(minCell - rectW + 1 - baseX, texW); kx <= Math.floorDiv(maxCell - baseX, texW); kx++) {
                    final int cellX0 = Math.max(baseX + kx * texW, minCell);
                    final int cellX1 = Math.min(baseX + kx * texW + rectW - 1, maxCell);
                    if (cellX0 > cellX1) continue;

                    final boolean touchesFineBox = fineX1 >= fineX0
                        && cellX1 >= fineX0 && cellX0 <= fineX1 && cellZ1 >= fineZ0 && cellZ0 <= fineZ1;

                    if (fineX1 < fineX0) {
                        clipToDisc(cellX0, cellZ0, cellX1, cellZ1, radiusCellsSq);
                    } else if (!touchesFineBox) {
                        if (outsideFineBox) clipToDisc(cellX0, cellZ0, cellX1, cellZ1, radiusCellsSq);
                    } else if (!outsideFineBox) {
                        clipToDisc(Math.max(cellX0, fineX0), Math.max(cellZ0, fineZ0),
                            Math.min(cellX1, fineX1), Math.min(cellZ1, fineZ1), radiusCellsSq);
                    } else {
                        clipToDisc(cellX0, cellZ0, cellX1, Math.min(cellZ1, fineZ0 - 1), radiusCellsSq);
                        clipToDisc(cellX0, Math.max(cellZ0, fineZ1 + 1), cellX1, cellZ1, radiusCellsSq);
                        final int midZ0 = Math.max(cellZ0, fineZ0);
                        final int midZ1 = Math.min(cellZ1, fineZ1);
                        clipToDisc(cellX0, midZ0, Math.min(cellX1, fineX0 - 1), midZ1, radiusCellsSq);
                        clipToDisc(Math.max(cellX0, fineX1 + 1), midZ0, cellX1, midZ1, radiusCellsSq);
                    }
                }
            }
        }
    }

    private void clipToDisc(int cellX0, int cellZ0, int cellX1, int cellZ1, int radiusCellsSq) {
        if (cellX0 > cellX1 || cellZ0 > cellZ1) return;

        final int farthestX = Math.max(Math.abs(cellX0), Math.abs(cellX1));
        final int farthestZ = Math.max(Math.abs(cellZ0), Math.abs(cellZ1));
        if (farthestX * farthestX + farthestZ * farthestZ <= radiusCellsSq) {
            addRect(cellX0, cellZ0, cellX1, cellZ1);
            return;
        }

        final int nearestX = (cellX0 <= 0 && cellX1 >= 0) ? 0 : Math.min(Math.abs(cellX0), Math.abs(cellX1));
        final int nearestZ = (cellZ0 <= 0 && cellZ1 >= 0) ? 0 : Math.min(Math.abs(cellZ0), Math.abs(cellZ1));
        if (nearestX * nearestX + nearestZ * nearestZ > radiusCellsSq) return;

        for (int cellZ = cellZ0; cellZ <= cellZ1; cellZ++) {
            final int halfWidth = (int) Math.sqrt(radiusCellsSq - cellZ * cellZ);
            final int rowX0 = Math.max(cellX0, -halfWidth);
            final int rowX1 = Math.min(cellX1, halfWidth);
            if (rowX0 > rowX1) continue;
            addRect(rowX0, cellZ, rowX1, cellZ);
        }
    }

    private void addRect(int cellX0, int cellZ0, int cellX1, int cellZ1) {
        int bucket = 0;
        if (groupByWedge) {
            final int nearestX = (cellX0 <= 0 && cellX1 >= 0) ? 0 : Math.min(Math.abs(cellX0), Math.abs(cellX1));
            final int nearestZ = (cellZ0 <= 0 && cellZ1 >= 0) ? 0 : Math.min(Math.abs(cellZ0), Math.abs(cellZ1));
            final boolean near = nearestX * nearestX + nearestZ * nearestZ <= ALWAYS_DRAWN_CELLS * ALWAYS_DRAWN_CELLS;

            final int wedge = wedgeOf(cellX0, cellZ0);
            if (!near
                && wedge == wedgeOf(cellX1 + 1, cellZ0) && wedge == wedgeOf(cellX0, cellZ1 + 1) && wedge == wedgeOf(cellX1 + 1, cellZ1 + 1)) {
                bucket = wedge + 1;
            }
        }

        if (count * 4 + 4 > rects.length) {
            rects = Arrays.copyOf(rects, rects.length * 2);
            rectWedgeBucket = Arrays.copyOf(rectWedgeBucket, rectWedgeBucket.length * 2);
        }
        final int slot = count * 4;
        rects[slot] = cellX0;
        rects[slot + 1] = cellZ0;
        rects[slot + 2] = cellX1;
        rects[slot + 3] = cellZ1;
        rectWedgeBucket[count] = bucket;
        wedgeBucketCount[bucket]++;
        count++;
    }

    private void orderByWedge() {
        int acc = 0;
        for (int b = 0; b <= WEDGE_COUNT + 1; b++) {
            final int bucketTotal = wedgeBucketCount[b];
            wedgeBucketCount[b] = acc;
            acc += bucketTotal;
        }
        if (orderedRects.length < count) orderedRects = new int[count];
        for (int i = 0; i < count; i++) {
            orderedRects[wedgeBucketCount[rectWedgeBucket[i]]++] = i;
        }
    }
}
