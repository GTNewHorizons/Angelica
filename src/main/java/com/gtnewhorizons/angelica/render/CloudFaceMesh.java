package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static com.gtnewhorizons.angelica.render.CloudDisc.ALWAYS_DRAWN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.MARGIN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGE_COUNT;
import static com.gtnewhorizons.angelica.render.CloudDisc.wedgeOf;

/**
 * Walls as one instanced face each.
 */
final class CloudFaceMesh {

    static final int DIR_DOWN = 0, DIR_UP = 1, DIR_NORTH = 2, DIR_SOUTH = 3, DIR_WEST = 4, DIR_EAST = 5;
    static final int CELL_LIMIT = 511;
    private static final int FLAG_INSIDE = 1;
    private static final int FACE_ATTRIB = 0;
    /** cellX 10b | cellZ 10b | dir 3b | flags 2b. */
    private static final int FACE_BYTES = 4;
    private static final int INTERIOR_FACES = 6;
    private static final int BUCKET_COUNT = WEDGE_COUNT + 1;
    private static final int BUCKET_ALWAYS_DRAWN = 0;
    private static final int BUCKET_WALL_WEDGE = 1;

    private static final Tracy.ZoneId Z_STAMP_WALLS = Tracy.zoneId("cloudFaceStampWalls", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_UPLOAD = Tracy.zoneId("cloudFaceUpload", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_REORDER = Tracy.zoneId("cloudFaceReorder", Tracy.COLOR_CLIENT);
    private final int[] wallStart = new int[WEDGE_COUNT + 1];
    private final int[] plateStart = new int[WEDGE_COUNT + 1];
    private int vao = -1;
    private int vbo = -1;
    private int ebo = -1;
    private int vboCapacityBytes;
    private int uploadedCount;
    private int stampedCount;
    private int interiorCount;
    private int[] faceData = new int[4096];
    private int[] faceSortKey = new int[4096];
    private int[] sortKeyStart = new int[0];
    private int ringsPerBucket = 1;
    private int buildAnchorX = Integer.MIN_VALUE, buildAnchorZ = Integer.MIN_VALUE;
    private int orderAnchorX = Integer.MIN_VALUE, orderAnchorZ = Integer.MIN_VALUE;
    private CloudShape shape;
    private ByteBuffer uploadBuffer;

    int uploadedCount() {
        return uploadedCount;
    }

    int interiorCount() {
        return interiorCount;
    }

    int buildAnchorX() {
        return buildAnchorX;
    }

    int buildAnchorZ() {
        return buildAnchorZ;
    }

    void clear() {
        uploadedCount = 0;
        interiorCount = 0;
    }

    void delete() {
        if (vao != -1) GLStateManager.glDeleteVertexArrays(vao);
        if (vbo != -1) GLStateManager.glDeleteBuffers(vbo);
        if (ebo != -1) GLStateManager.glDeleteBuffers(ebo);
        vao = -1;
        vbo = -1;
        ebo = -1;
        vboCapacityBytes = 0;
        clear();
        stampedCount = 0;
        ringsPerBucket = 1;
        buildAnchorX = Integer.MIN_VALUE;
        buildAnchorZ = Integer.MIN_VALUE;
        orderAnchorX = Integer.MIN_VALUE;
        orderAnchorZ = Integer.MIN_VALUE;
        shape = null;
        uploadBuffer = null;
        Arrays.fill(wallStart, 0);
        Arrays.fill(plateStart, 0);
    }

    void build(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq, int wallCutCells) {
        stampedCount = 0;
        this.shape = shape;
        ringsPerBucket = 2 * (radiusCells + MARGIN_CELLS) + 1;
        buildAnchorX = anchorX;
        buildAnchorZ = anchorZ;
        orderAnchorX = anchorX;
        orderAnchorZ = anchorZ;

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_STAMP_WALLS);
        addWalls(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        if (Tracy.FINE_ZONES) Tracy.endZone();

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_UPLOAD);
        finishBuild();
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    private static int packFace(int cellX, int cellZ, int dir, int flags) {
        return ((cellX + 512) & 0x3FF)
            | (((cellZ + 512) & 0x3FF) << 10)
            | (dir << 20)
            | (flags << 23);
    }

    private void addFace(int cellX, int cellZ, int dir, int bucket, int ring) {
        if (stampedCount + INTERIOR_FACES == faceSortKey.length) {
            faceSortKey = Arrays.copyOf(faceSortKey, faceSortKey.length * 2);
            faceData = Arrays.copyOf(faceData, faceData.length * 2);
        }
        faceData[stampedCount] = packFace(cellX, cellZ, dir, 0);
        faceSortKey[stampedCount] = bucket * ringsPerBucket + ring;
        stampedCount++;
    }

    private void stampInterior(int anchorX, int anchorZ) {
        interiorCount = 0;
        if (shape == null || (shape.cellFlagsAt(anchorX, anchorZ) & CloudShape.OPAQUE) == 0) return;

        final int driftX = anchorX - buildAnchorX;
        final int driftZ = anchorZ - buildAnchorZ;
        for (int dir = DIR_DOWN; dir <= DIR_EAST; dir++) {
            faceData[uploadedCount + dir] = packFace(driftX, driftZ, dir, FLAG_INSIDE);
        }
        interiorCount = INTERIOR_FACES;
    }

    private void addWalls(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq, int wallCutCells) {
        stampWallRuns(shape, shape.westRuns, DIR_WEST, true, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.eastRuns, DIR_EAST, true, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.northRuns, DIR_NORTH, false, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.southRuns, DIR_SOUTH, false, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
    }

    private void stampWallRuns(CloudShape shape, int[] runs, int dir, boolean alongZ, int anchorX, int anchorZ,
                               int radiusCells, int radiusCellsSq, int wallCutCells) {
        final int texW = shape.width;
        final int texH = shape.height;
        final int minCell = -radiusCells;
        final int maxCell = radiusCells;
        final int nearAlwaysSq = ALWAYS_DRAWN_CELLS * ALWAYS_DRAWN_CELLS;
        final int wallCutSq = wallCutCells >= radiusCells ? Integer.MAX_VALUE : wallCutCells * wallCutCells;

        for (int r = 0; r < runs.length; r += 3) {
            final int runLength = runs[r + 2];
            final int baseX = Math.floorMod(runs[r] - anchorX, texW);
            final int baseZ = Math.floorMod(runs[r + 1] - anchorZ, texH);

            for (int kz = Math.floorDiv(minCell - baseZ - (alongZ ? runLength - 1 : 0), texH); kz <= Math.floorDiv(maxCell - baseZ, texH); kz++) {
                final int runZ = baseZ + kz * texH;
                for (int kx = Math.floorDiv(minCell - baseX - (alongZ ? 0 : runLength - 1), texW); kx <= Math.floorDiv(maxCell - baseX, texW); kx++) {
                    final int runX = baseX + kx * texW;

                    for (int step = 0; step < runLength; step++) {
                        final int cellX = alongZ ? runX : runX + step;
                        final int cellZ = alongZ ? runZ + step : runZ;

                        if (dir == DIR_WEST ? cellX < 1 - MARGIN_CELLS
                            : dir == DIR_EAST ? cellX > MARGIN_CELLS - 1
                            : dir == DIR_NORTH ? cellZ < 1 - MARGIN_CELLS
                            : cellZ > MARGIN_CELLS - 1) {
                            continue;
                        }

                        final int distSq = cellX * cellX + cellZ * cellZ;
                        if (distSq > radiusCellsSq) continue;
                        final boolean near = distSq <= nearAlwaysSq;
                        if (!near && distSq > wallCutSq) continue;

                        final int bucket = near ? BUCKET_ALWAYS_DRAWN : BUCKET_WALL_WEDGE + wedgeOf(cellX, cellZ);
                        addFace(cellX, cellZ, dir, bucket, Math.abs(cellX) + Math.abs(cellZ));
                    }
                }
            }
        }
    }

    boolean orderStale(int anchorX, int anchorZ) {
        return anchorX != orderAnchorX || anchorZ != orderAnchorZ;
    }

    void reorder(int anchorX, int anchorZ) {
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_REORDER);
        try {
            final int driftX = anchorX - buildAnchorX;
            final int driftZ = anchorZ - buildAnchorZ;
            final int nearAlwaysSq = ALWAYS_DRAWN_CELLS * ALWAYS_DRAWN_CELLS;
            for (int i = 0; i < uploadedCount; i++) {
                final int packed = faceData[i];
                final int cellX = (packed & 0x3FF) - 512 - driftX;
                final int cellZ = ((packed >> 10) & 0x3FF) - 512 - driftZ;
                final int distSq = cellX * cellX + cellZ * cellZ;
                final int bucket = distSq <= nearAlwaysSq ? BUCKET_ALWAYS_DRAWN : BUCKET_WALL_WEDGE + wedgeOf(cellX, cellZ);
                final int ring = Math.min(Math.abs(cellX) + Math.abs(cellZ), ringsPerBucket - 1);
                faceSortKey[i] = bucket * ringsPerBucket + ring;
            }
            stampInterior(anchorX, anchorZ);
            sortAndUpload();
            orderAnchorX = anchorX;
            orderAnchorZ = anchorZ;
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
    }

    private void finishBuild() {
        uploadedCount = stampedCount;
        stampInterior(buildAnchorX, buildAnchorZ);
        if (uploadedCount == 0) Arrays.fill(wallStart, 0);
        sortAndUpload();
    }

    private void sortAndUpload() {
        final int sortKeyCount = BUCKET_COUNT * ringsPerBucket;
        if (sortKeyStart.length < sortKeyCount + 1) sortKeyStart = new int[sortKeyCount + 1];
        else Arrays.fill(sortKeyStart, 0, sortKeyCount + 1, 0);

        for (int i = 0; i < uploadedCount; i++) sortKeyStart[faceSortKey[i]]++;
        int acc = 0;
        for (int k = 0; k < sortKeyCount; k++) {
            final int keyTotal = sortKeyStart[k];
            sortKeyStart[k] = acc;
            acc += keyTotal;
        }
        sortKeyStart[sortKeyCount] = acc;

        for (int w = 0; w < WEDGE_COUNT; w++) wallStart[w] = sortKeyStart[(BUCKET_WALL_WEDGE + w) * ringsPerBucket];
        wallStart[WEDGE_COUNT] = uploadedCount;

        final int totalFaces = uploadedCount + interiorCount;
        if (totalFaces == 0) return;

        final int uploadBytes = totalFaces * FACE_BYTES;
        if (uploadBuffer == null || uploadBuffer.capacity() < uploadBytes) {
            uploadBuffer = BufferUtils.createByteBuffer(Math.max(uploadBytes, 8192));
        }
        uploadBuffer.clear();
        final IntBuffer ints = uploadBuffer.asIntBuffer();
        for (int i = 0; i < uploadedCount; i++) {
            ints.put(sortKeyStart[faceSortKey[i]]++, faceData[i]);
        }
        for (int i = 0; i < interiorCount; i++) {
            ints.put(uploadedCount + i, faceData[uploadedCount + i]);
        }
        uploadBuffer.position(0).limit(uploadBytes);

        final boolean freshBuffers = vbo == -1;
        if (freshBuffers) vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        if (uploadBytes > vboCapacityBytes) {
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, uploadBuffer, GL15.GL_DYNAMIC_DRAW);
            vboCapacityBytes = uploadBytes;
        } else {
            GLStateManager.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, uploadBuffer);
        }

        if (freshBuffers) {
            ensureQuadIndices();
            if (vao == -1) vao = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glEnableVertexAttribArray(FACE_ATTRIB);
            GLStateManager.glVertexAttribDivisor(FACE_ATTRIB, 1);
            GLStateManager.glVertexAttribIPointer(FACE_ATTRIB, 1, GL11.GL_INT, FACE_BYTES, 0L);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GLStateManager.glBindVertexArray(0);
        }
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void ensureQuadIndices() {
        if (ebo != -1) return;
        ebo = GLStateManager.glGenBuffers();
        final IntBuffer indices = BufferUtils.createIntBuffer(6);
        indices.put(0).put(1).put(2).put(0).put(2).put(3);
        indices.flip();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    void bind() {
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    }

    void unbind() {
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);
    }

    void drawRange(int startFace, int endFace) {
        final int faces = endFace - startFace;
        if (faces <= 0) return;
        GLStateManager.glVertexAttribIPointer(FACE_ATTRIB, 1, GL11.GL_INT, FACE_BYTES, (long) startFace * FACE_BYTES);
        GLStateManager.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L, faces);
    }

    void drawInterior() {
        drawRange(uploadedCount, uploadedCount + interiorCount);
    }

    int[] plateStarts() {
        return plateStart;
    }

    int[] wallStarts() {
        return wallStart;
    }
}
