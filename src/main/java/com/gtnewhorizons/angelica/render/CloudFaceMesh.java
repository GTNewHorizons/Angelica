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
    private static final int BUCKET_COUNT = WEDGE_COUNT + 1;
    private static final int BUCKET_ALWAYS_DRAWN = 0;
    private static final int BUCKET_WALL_WEDGE = 1;

    private static final Tracy.ZoneId Z_STAMP_WALLS = Tracy.zoneId("cloudFaceStampWalls", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_UPLOAD = Tracy.zoneId("cloudFaceUpload", Tracy.COLOR_CLIENT);
    private final int[] wallStart = new int[WEDGE_COUNT + 1];
    private final int[] plateStart = new int[WEDGE_COUNT + 1];
    private int vao = -1;
    private int vbo = -1;
    private int ebo = -1;
    private int vboCapacityBytes;
    private int uploadedCount;
    private int stampedCount;
    private int[] faceData = new int[8192];
    private int[] faceSortKey = new int[4096];
    private int[] sortKeyStart = new int[0];
    private int ringsPerBucket = 1;
    private ByteBuffer uploadBuffer;

    int uploadedCount() {
        return uploadedCount;
    }

    void clear() {
        uploadedCount = 0;
    }

    void build(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq, int wallCutCells) {
        stampedCount = 0;
        ringsPerBucket = 2 * radiusCells + 1;

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_STAMP_WALLS);
        addWalls(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        if (Tracy.FINE_ZONES) Tracy.endZone();

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_UPLOAD);
        finishBuild();
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    private void addFace(int cellX, int cellZ, int dir, int flags, int width, int height, int bucket, int ring) {
        if (stampedCount == faceSortKey.length) {
            faceSortKey = Arrays.copyOf(faceSortKey, faceSortKey.length * 2);
            faceData = Arrays.copyOf(faceData, faceData.length * 2);
        }
        faceData[stampedCount * 2] = ((cellX + 512) & 0x3FF)
            | (((cellZ + 512) & 0x3FF) << 10)
            | (dir << 20)
            | (flags << 23);
        faceData[stampedCount * 2 + 1] = (width & 0xFFFF) | (height << 16);
        faceSortKey[stampedCount] = bucket * ringsPerBucket + ring;
        stampedCount++;
    }

    private void addWalls(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq, int wallCutCells) {
        stampWallRuns(shape, shape.westRuns, DIR_WEST, true, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.eastRuns, DIR_EAST, true, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.northRuns, DIR_NORTH, false, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
        stampWallRuns(shape, shape.southRuns, DIR_SOUTH, false, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);

        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if ((shape.cellFlagsAt(anchorX + offsetX, anchorZ + offsetZ) & CloudShape.OPAQUE) == 0) continue;
                final int ring = Math.abs(offsetX) + Math.abs(offsetZ);
                for (int dir = DIR_DOWN; dir <= DIR_EAST; dir++) {
                    addFace(offsetX, offsetZ, dir, FLAG_INSIDE, 1, 1, BUCKET_ALWAYS_DRAWN, ring);
                }
            }
        }
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

                        if (dir == DIR_WEST ? cellX < 1 : dir == DIR_EAST ? cellX > -1 : dir == DIR_NORTH ? cellZ < 1 : cellZ > -1) {
                            continue;
                        }

                        final int distSq = cellX * cellX + cellZ * cellZ;
                        if (distSq > radiusCellsSq) continue;
                        final boolean near = distSq <= nearAlwaysSq;
                        if (!near && distSq > wallCutSq) continue;

                        final int bucket = near ? BUCKET_ALWAYS_DRAWN : BUCKET_WALL_WEDGE + wedgeOf(cellX, cellZ);
                        addFace(cellX, cellZ, dir, 0, 1, 1, bucket, Math.abs(cellX) + Math.abs(cellZ));
                    }
                }
            }
        }
    }

    private void finishBuild() {
        uploadedCount = stampedCount;
        if (uploadedCount == 0) {
            Arrays.fill(wallStart, 0);
            return;
        }

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

        final int uploadBytes = uploadedCount * 8;
        if (uploadBuffer == null || uploadBuffer.capacity() < uploadBytes) {
            uploadBuffer = BufferUtils.createByteBuffer(Math.max(uploadBytes, 8192));
        }
        uploadBuffer.clear();
        final IntBuffer ints = uploadBuffer.asIntBuffer();
        for (int i = 0; i < uploadedCount; i++) {
            final int slot = sortKeyStart[faceSortKey[i]]++;
            ints.put(slot * 2, faceData[i * 2]);
            ints.put(slot * 2 + 1, faceData[i * 2 + 1]);
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
            GLStateManager.glVertexAttribIPointer(FACE_ATTRIB, 2, GL11.GL_INT, 8, 0L);
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
        GLStateManager.glVertexAttribIPointer(FACE_ATTRIB, 2, GL11.GL_INT, 8, (long) startFace * 8L);
        GLStateManager.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L, faces);
    }

    int[] plateStarts() {
        return plateStart;
    }

    int[] wallStarts() {
        return wallStart;
    }
}
