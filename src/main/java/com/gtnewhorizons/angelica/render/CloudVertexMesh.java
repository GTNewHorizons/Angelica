package com.gtnewhorizons.angelica.render;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

import static com.gtnewhorizons.angelica.render.CloudDisc.ALWAYS_DRAWN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGE_COUNT;
import static com.gtnewhorizons.angelica.render.CloudDisc.wedgeOf;
import static org.joml.Math.clamp;

/**
 * Plates and walls as vertices.
 */
final class CloudVertexMesh {

    static final byte FACE_DOWN = 0, FACE_UP = 1, FACE_SIDE_X = 2, FACE_SIDE_Z = 3;
    static final float[] FACE_SHADE = {0.7f, 1.0f, 0.9f, 0.8f};
    private static final int[] FACE_SHADE_PACKED;
    private static final int FACE_SHADE_ATTRIB = 5;
    private static final int STRIDE_WITH_UV = 24;
    private static final int NORMAL_OFFSET_WITH_UV = 20;
    private static final int STRIDE_NO_UV = 16;
    private static final int NORMAL_OFFSET_NO_UV = 12;
    private static final float EDGE_OVERLAP = 0.0001f;
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private static final int NORMAL_POS_X = packNormal((byte) 127, (byte) 0, (byte) 0);
    private static final int NORMAL_NEG_X = packNormal((byte) -127, (byte) 0, (byte) 0);
    private static final int NORMAL_POS_Y = packNormal((byte) 0, (byte) 127, (byte) 0);
    private static final int NORMAL_NEG_Y = packNormal((byte) 0, (byte) -127, (byte) 0);
    private static final int NORMAL_POS_Z = packNormal((byte) 0, (byte) 0, (byte) 127);
    private static final int NORMAL_NEG_Z = packNormal((byte) 0, (byte) 0, (byte) -127);
    private static final int DIR_WEST = 0, DIR_EAST = 1, DIR_NORTH = 2, DIR_SOUTH = 3;
    private static final int DIR_NONE = 4;
    private static final int GROUP_PLATE_ALWAYS = 0, GROUP_PLATE_WEDGE = 1, GROUP_ALWAYS_DRAWN = 2, GROUP_WALL_WEDGE = 3;
    private static final int ORDER_GROUP_COUNT = 2 * WEDGE_COUNT + 3;
    private static final int ORDER_BANDS_PER_CELL = 4;
    private static final int WEDGE_BUCKETS = WEDGE_COUNT + 1;
    private static final int FACE_WEDGE_BUCKETS = 4 * WEDGE_BUCKETS;
    private static final int WALL_SORT_INDEX_BITS = 26;
    private static final int WALL_SORT_DISTANCE_BITS = 20;
    private static final int WALL_SORT_BUCKET_SHIFT = WALL_SORT_INDEX_BITS + WALL_SORT_DISTANCE_BITS;
    private static final long WALL_SORT_INDEX_MASK = (1L << WALL_SORT_INDEX_BITS) - 1;
    private static final int WALL_SORT_MAX_RUNS = 1 << WALL_SORT_INDEX_BITS;
    private static final int WALL_SORT_MAX_DISTANCE = (1 << WALL_SORT_DISTANCE_BITS) - 1;
    private static final int RADIX_BITS = 10;
    private static final int RADIX_SIZE = 1 << RADIX_BITS;
    private static final int RADIX_MASK = RADIX_SIZE - 1;
    private static final Tracy.ZoneId Z_STAMP_PLATES = Tracy.zoneId("cloudVertexStampPlates", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_PLATE_COVER = Tracy.zoneId("cloudPlateCover", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_STAMP_WALLS = Tracy.zoneId("cloudVertexStampWalls", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_UPLOAD = Tracy.zoneId("cloudVertexUpload", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_GEOMETRY = Tracy.zoneId("cloudVertexGeometry", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_REORDER = Tracy.zoneId("cloudVertexReorder", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_WALL_SORT = Tracy.zoneId("cloudVertexWallSort", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_REORDER_UPLOAD = Tracy.zoneId("cloudVertexReorderUpload", Tracy.COLOR_CLIENT);

    static {
        FACE_SHADE_PACKED = new int[4];
        for (int i = 0; i < 4; i++) {
            final int shadeByte = Math.round(FACE_SHADE[i] * 255f) & 0xFF;
            FACE_SHADE_PACKED[i] = BIG_ENDIAN ? shadeByte : (shadeByte << 24);
        }
    }

    private final int[] faceWedgeStart = new int[FACE_WEDGE_BUCKETS + 1];
    private final int[] faceWedgeCursor = new int[FACE_WEDGE_BUCKETS];
    private final int[] wallStart = new int[WEDGE_COUNT + 1];
    private final int[] plateStart = new int[WEDGE_COUNT + 1];
    private final int[] wallStartScratch = new int[WEDGE_COUNT + 1];
    private final int[] plateStartScratch = new int[WEDGE_COUNT + 1];
    private int orderPendingQuads;
    private int orderPendingAnchorX, orderPendingAnchorZ;
    private final CloudPlateCover plates = new CloudPlateCover();
    private IVertexArrayObject vao;
    private int vertexCount;
    private int eboId = -1;
    private int eboQuadCapacity;
    private boolean eboIsSequential;
    private boolean vaoHasUv = true;
    private int vertexStride = STRIDE_WITH_UV;
    private int normalOffset = NORMAL_OFFSET_WITH_UV;
    private int quadBytes = 4 * STRIDE_WITH_UV;
    private ByteBuffer emitBuffer;
    private long emitBufferAddr;
    private int emitBufferCapacity;
    private long writeAddr, writeEnd;
    private ByteBuffer sortedBuffer;
    private long sortedBufferAddr;
    private int sortedBufferCapacity;
    private byte[] quadFace = new byte[2048];
    private int quadCount;
    private int[] quadCell = new int[2048];
    private short[] quadGroupBits = new short[2048];
    private int nextQuadCellX, nextQuadCellZ, nextQuadGroup, nextQuadWedge, nextQuadDir = DIR_NONE;
    private int buildAnchorX = Integer.MIN_VALUE, buildAnchorZ = Integer.MIN_VALUE;
    private int orderAnchorX = Integer.MIN_VALUE, orderAnchorZ = Integer.MIN_VALUE;
    private int buildRadiusCells;
    private int[] orderBucketCount = new int[0];
    private int[] quadBucket = new int[0];
    private IntBuffer orderIndexBuffer;
    private int[] orderIndexScratch = new int[0];
    private int[] quadSortKey = new int[0];
    private int[] wallRuns = new int[4096 * 4];
    private int wallRunCount;
    private int[] wallRunBucket = new int[4096];
    private int[] wallRunDistance = new int[4096];
    private long[] wallSortKey = new long[4096];
    private long[] wallSortScratch = new long[4096];
    private final int[] radixCounts = new int[RADIX_SIZE];
    private float textureScaleX, textureScaleZ;
    private boolean forPackShader;
    private int wallCutCells = Integer.MAX_VALUE;
    private int wallClipRadiusSq;
    private ByteBuffer pendingUpload;
    private int pendingVertices = -1;
    private int vboCapacityVertices;
    private int uploadCursorBytes = -1;
    private int uploadTotalBytes;
    private int uploadVertices;
    private static final Tracy.ZoneId Z_EBO = Tracy.zoneId("cloudVertexEbo", Tracy.COLOR_CLIENT);
    private static IntBuffer sharedSequentialIndices;
    private static int sharedSequentialQuads;

    private static int packNormal(byte nx, byte ny, byte nz) {
        final int x = nx & 0xFF, y = ny & 0xFF, z = nz & 0xFF;
        return BIG_ENDIAN ? (x << 24) | (y << 16) | (z << 8) : x | (y << 8) | (z << 16);
    }

    int quadCount() {
        return vertexCount / 4;
    }

    boolean built() {
        return vao != null && vertexCount != 0;
    }

    void clear() {
        vertexCount = 0;
        orderAnchorX = Integer.MIN_VALUE;
        orderAnchorZ = Integer.MIN_VALUE;
    }

    int[] plateStarts() {
        return plateStart;
    }

    int[] wallStarts() {
        return wallStart;
    }

    void buildFast(CloudShape shape, int radiusCells, float scrollX, float scrollZ, boolean forPackShader) {
        beginBuild(shape, false, forPackShader);

        nextQuadDir = DIR_NONE;
        nextQuadGroup = GROUP_PLATE_ALWAYS;
        nextQuadWedge = 0;
        nextQuadCellX = 0;
        nextQuadCellZ = 0;
        final double minCell = -radiusCells;
        final double maxCell = radiusCells;
        final float uWest = -radiusCells * textureScaleX + scrollX;
        final float uEast = radiusCells * textureScaleX + scrollX;
        final float vNorth = -radiusCells * textureScaleZ + scrollZ;
        final float vSouth = radiusCells * textureScaleZ + scrollZ;
        ensureEmitCapacity(quadBytes);
        emitQuad(minCell, 0.0, minCell, uWest, vNorth, maxCell, 0.0, minCell, uEast, vNorth,
            maxCell, 0.0, maxCell, uEast, vSouth, minCell, 0.0, maxCell, uWest, vSouth, FACE_UP, NORMAL_POS_Y);

        finishGeometry();
    }

    void build(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq,
               double cellHeightBlocks, float scrollX, float scrollZ,
               boolean emitUnderside, boolean emitTopSurface, boolean emitPlates,
               int wallCut, int plateLodCells, boolean untextured, boolean forPack) {
        beginBuild(shape, untextured, forPack);
        this.wallCutCells = wallCut;
        buildAnchorX = anchorX;
        buildAnchorZ = anchorZ;
        buildRadiusCells = radiusCells;

        final double deckTopY = cellHeightBlocks;
        final double plateTopY = deckTopY - EDGE_OVERLAP;

        if (emitPlates) {
            if (Tracy.FINE_ZONES) Tracy.beginZone(Z_STAMP_PLATES);
            addPlates(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, emitUnderside, emitTopSurface, plateTopY, scrollX, scrollZ, plateLodCells);
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_STAMP_WALLS);
        addWalls(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, deckTopY, scrollX, scrollZ);
        if (Tracy.FINE_ZONES) Tracy.endZone();

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_GEOMETRY);
        finishGeometry();
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    void buildInterior(CloudShape shape, int anchorX, int anchorZ, double deckTopY,
                       float scrollX, float scrollZ, boolean untextured, boolean forPack) {
        beginBuild(shape, untextured, forPack);
        addInteriorFaces(shape, anchorX, anchorZ, deckTopY, scrollX, scrollZ);
        finishGeometry();
    }

    private void beginBuild(CloudShape shape, boolean untextured, boolean forPack) {
        textureScaleX = 1.0f / shape.width;
        textureScaleZ = 1.0f / shape.height;
        forPackShader = forPack;
        if (!forPack && quadCell.length < quadFace.length) {
            quadCell = new int[quadFace.length];
            quadGroupBits = new short[quadFace.length];
        }
        final boolean withUv = !untextured;
        vertexStride = withUv ? STRIDE_WITH_UV : STRIDE_NO_UV;
        normalOffset = withUv ? NORMAL_OFFSET_WITH_UV : NORMAL_OFFSET_NO_UV;
        quadBytes = 4 * vertexStride;
        prepareEmitBuffer();
    }

    private void addPlates(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq,
                           boolean emitUnderside, boolean emitTopSurface, double plateTopY, float scrollX, float scrollZ, int plateLodCells) {
        final int bytesPerRect = (emitUnderside ? quadBytes : 0) + (emitTopSurface ? quadBytes : 0);
        if (bytesPerRect == 0) return;

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_PLATE_COVER);
        plates.build(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, plateLodCells, !forPackShader);
        if (Tracy.FINE_ZONES) Tracy.endZone();

        if (forPackShader) {
            nextQuadDir = DIR_NONE;
            nextQuadGroup = GROUP_PLATE_ALWAYS;
            nextQuadWedge = 0;
            if (emitUnderside) emitPlatePass(true, false, plateTopY, scrollX, scrollZ);
            if (emitTopSurface) emitPlatePass(false, true, plateTopY, scrollX, scrollZ);
            return;
        }

        for (int i = 0; i < plates.count(); i++) {
            final int rect = plates.orderedRect(i);
            final int wedgeBucket = plates.wedgeBucket(rect);
            nextQuadDir = DIR_NONE;
            nextQuadGroup = wedgeBucket == 0 ? GROUP_PLATE_ALWAYS : GROUP_PLATE_WEDGE;
            nextQuadWedge = wedgeBucket == 0 ? 0 : wedgeBucket - 1;
            nextQuadCellX = plates.minX(rect);
            nextQuadCellZ = plates.minZ(rect);
            emitPlateRect(plates.minX(rect), plates.minZ(rect), plates.maxX(rect) + 1, plates.maxZ(rect) + 1,
                emitUnderside, emitTopSurface, plateTopY, scrollX, scrollZ, bytesPerRect);
        }
    }

    private void emitPlatePass(boolean emitUnderside, boolean emitTopSurface, double plateTopY, float scrollX, float scrollZ) {
        for (int rect = 0; rect < plates.count(); rect++) {
            nextQuadCellX = plates.minX(rect);
            nextQuadCellZ = plates.minZ(rect);
            emitPlateRect(plates.minX(rect), plates.minZ(rect), plates.maxX(rect) + 1, plates.maxZ(rect) + 1,
                emitUnderside, emitTopSurface, plateTopY, scrollX, scrollZ, quadBytes);
        }
    }

    private void addWalls(CloudShape shape, int anchorX, int anchorZ, int radiusCells, int radiusCellsSq,
                          double deckTopY, float scrollX, float scrollZ) {
        final int texW = shape.width;
        final int texH = shape.height;

        final int bandsPerWedge = radiusCells + 1;
        final long cutCellsSq = (long) wallCutCells * wallCutCells;
        wallClipRadiusSq = forPackShader && cutCellsSq < radiusCellsSq ? (int) cutCellsSq : radiusCellsSq;
        wallRunCount = 0;
        collectWallRuns(shape.westRuns, DIR_WEST, anchorX, anchorZ, texW, texH, bandsPerWedge);
        collectWallRuns(shape.eastRuns, DIR_EAST, anchorX, anchorZ, texW, texH, bandsPerWedge);
        collectWallRuns(shape.northRuns, DIR_NORTH, anchorX, anchorZ, texW, texH, bandsPerWedge);
        collectWallRuns(shape.southRuns, DIR_SOUTH, anchorX, anchorZ, texW, texH, bandsPerWedge);
        final int found = wallRunCount;

        if (found == 0) return;

        if (forPackShader) {
            nextQuadGroup = GROUP_ALWAYS_DRAWN;
            nextQuadWedge = 0;
            for (int i = 0; i < found; i++) {
                final int run = i * 4;
                nextQuadDir = wallRuns[run];
                nextQuadCellX = wallRuns[run + 1];
                nextQuadCellZ = wallRuns[run + 2];
                emitWallRun(wallRuns[run], wallRuns[run + 1], wallRuns[run + 2], wallRuns[run + 3], deckTopY, scrollX, scrollZ);
            }
            return;
        }

        if (wallSortKey.length < found) wallSortKey = new long[found];
        for (int i = 0; i < found; i++) {
            wallSortKey[i] = ((long) wallRunBucket[i] << WALL_SORT_BUCKET_SHIFT)
                | ((long) wallRunDistance[i] << WALL_SORT_INDEX_BITS)
                | i;
        }
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_WALL_SORT);
        radixSortWallKeys(found);
        if (Tracy.FINE_ZONES) Tracy.endZone();

        for (int i = 0; i < found; i++) {
            final long key = wallSortKey[i];
            final int bucket = (int) (key >>> WALL_SORT_BUCKET_SHIFT);
            final int wedge = bucket == 0 ? -1 : (bucket - 1) / bandsPerWedge;
            final int run = (int) (key & WALL_SORT_INDEX_MASK) * 4;
            nextQuadGroup = bucket == 0 ? GROUP_ALWAYS_DRAWN : GROUP_WALL_WEDGE;
            nextQuadWedge = bucket == 0 ? 0 : wedge;
            nextQuadDir = wallRuns[run];
            nextQuadCellX = wallRuns[run + 1];
            nextQuadCellZ = wallRuns[run + 2];
            emitWallRun(wallRuns[run], wallRuns[run + 1], wallRuns[run + 2], wallRuns[run + 3], deckTopY, scrollX, scrollZ);
        }
    }

    private void collectWallRuns(int[] runs, int dir, int anchorX, int anchorZ, int texW, int texH, int bandsPerWedge) {
        final boolean alongZ = dir == DIR_WEST || dir == DIR_EAST;
        final int clipRadiusCells = (int) Math.sqrt(wallClipRadiusSq);
        final int minCell = -clipRadiusCells;
        final int maxCell = clipRadiusCells;

        for (int r = 0; r < runs.length; r += 3) {
            final int len = runs[r + 2];
            final int baseX = Math.floorMod(runs[r] - anchorX, texW);
            final int baseZ = Math.floorMod(runs[r + 1] - anchorZ, texH);

            for (int kz = Math.floorDiv(minCell - baseZ - (alongZ ? len - 1 : 0), texH); kz <= Math.floorDiv(maxCell - baseZ, texH); kz++) {
                final int runZ = baseZ + kz * texH;
                for (int kx = Math.floorDiv(minCell - baseX - (alongZ ? 0 : len - 1), texW); kx <= Math.floorDiv(maxCell - baseX, texW); kx++) {
                    final int runX = baseX + kx * texW;

                    final int startX, startZ, span;
                    if (alongZ) {
                        if (runX * runX > wallClipRadiusSq) continue;
                        final int halfSpan = (int) Math.sqrt(wallClipRadiusSq - runX * runX);
                        final int z0 = Math.max(runZ, -halfSpan);
                        final int z1 = Math.min(runZ + len - 1, halfSpan);
                        if (z0 > z1) continue;
                        startX = runX;
                        startZ = z0;
                        span = z1 - z0 + 1;
                    } else {
                        if (runZ * runZ > wallClipRadiusSq) continue;
                        final int halfSpan = (int) Math.sqrt(wallClipRadiusSq - runZ * runZ);
                        final int x0 = Math.max(runX, -halfSpan);
                        final int x1 = Math.min(runX + len - 1, halfSpan);
                        if (x0 > x1) continue;
                        startX = x0;
                        startZ = runZ;
                        span = x1 - x0 + 1;
                    }

                    if (forPackShader) {
                        recordWallRun(dir, startX, startZ, span, 0, 0);
                        continue;
                    }

                    int pieceStart = 0;
                    int pieceWedge = wedgeOf(startX, startZ);
                    double pieceDist = Math.sqrt(startX * startX + startZ * startZ);
                    int pieceBand = (int) pieceDist;
                    for (int c = 1; c <= span; c++) {
                        final int cX = alongZ ? startX : startX + c;
                        final int cZ = alongZ ? startZ + c : startZ;
                        final boolean last = c == span;
                        final int wedgeHere = last ? -1 : wedgeOf(cX, cZ);
                        final double distHere = last ? -1.0 : Math.sqrt(cX * cX + cZ * cZ);
                        final int bandHere = last ? -1 : (int) distHere;
                        if (wedgeHere == pieceWedge && bandHere == pieceBand) {
                            if (distHere < pieceDist) pieceDist = distHere;
                            continue;
                        }

                        if (pieceBand <= wallCutCells) {
                            recordWallRun(dir,
                                alongZ ? startX : startX + pieceStart,
                                alongZ ? startZ + pieceStart : startZ,
                                c - pieceStart,
                                pieceBand <= ALWAYS_DRAWN_CELLS ? 0 : 1 + pieceWedge * bandsPerWedge + pieceBand,
                                (int) (pieceDist * 256.0));
                        }

                        pieceStart = c;
                        pieceWedge = wedgeHere;
                        pieceDist = distHere;
                        pieceBand = bandHere;
                    }
                }
            }
        }
    }

    private void radixSortWallKeys(int count) {
        if (count < 2) return;
        if (wallSortScratch.length < count) wallSortScratch = new long[count];

        long[] src = wallSortKey;
        long[] dst = wallSortScratch;
        final int[] counts = radixCounts;

        for (int shift = WALL_SORT_INDEX_BITS; shift < Long.SIZE; shift += RADIX_BITS) {
            Arrays.fill(counts, 0);
            for (int i = 0; i < count; i++) counts[(int) ((src[i] >>> shift) & RADIX_MASK)]++;

            boolean uniform = false;
            int acc = 0;
            for (int d = 0; d < RADIX_SIZE; d++) {
                final int c = counts[d];
                if (c == count) uniform = true;
                counts[d] = acc;
                acc += c;
            }
            if (uniform) continue;

            for (int i = 0; i < count; i++) dst[counts[(int) ((src[i] >>> shift) & RADIX_MASK)]++] = src[i];
            final long[] swap = src;
            src = dst;
            dst = swap;
        }

        if (src != wallSortKey) System.arraycopy(src, 0, wallSortKey, 0, count);
    }

    private void recordWallRun(int dir, int relX, int relZ, int span, int bucket, int distance256ths) {
        if (wallRunCount == WALL_SORT_MAX_RUNS) return;
        if (wallRunCount * 4 + 4 > wallRuns.length) {
            wallRuns = Arrays.copyOf(wallRuns, wallRuns.length * 2);
            wallRunBucket = Arrays.copyOf(wallRunBucket, wallRunBucket.length * 2);
            wallRunDistance = Arrays.copyOf(wallRunDistance, wallRunDistance.length * 2);
        }
        final int slot = wallRunCount * 4;
        wallRuns[slot] = dir;
        wallRuns[slot + 1] = relX;
        wallRuns[slot + 2] = relZ;
        wallRuns[slot + 3] = span;
        wallRunBucket[wallRunCount] = bucket;
        wallRunDistance[wallRunCount] = Math.min(distance256ths, WALL_SORT_MAX_DISTANCE);
        wallRunCount++;
    }

    private void emitWallRun(int dir, int relX, int relZ, int span, double deckTopY, float scrollX, float scrollZ) {
        ensureEmitCapacity(quadBytes);
        final double x0 = relX;
        final double z0 = relZ;

        if (dir == DIR_WEST || dir == DIR_EAST) {
            final double z1 = relZ + span;
            final float uCentre = (relX + 0.5F) * textureScaleX + scrollX;
            final float vNorth = relZ * textureScaleZ + scrollZ;
            final float vSouth = (relZ + span) * textureScaleZ + scrollZ;
            if (dir == DIR_WEST) {
                emitQuad(x0, 0.0, z1, uCentre, vSouth, x0, deckTopY, z1, uCentre, vSouth,
                    x0, deckTopY, z0, uCentre, vNorth, x0, 0.0, z0, uCentre, vNorth, FACE_SIDE_X, NORMAL_NEG_X);
            } else {
                final double xEast = relX + 1 - EDGE_OVERLAP;
                emitQuad(xEast, 0.0, z0, uCentre, vNorth, xEast, deckTopY, z0, uCentre, vNorth,
                    xEast, deckTopY, z1, uCentre, vSouth, xEast, 0.0, z1, uCentre, vSouth, FACE_SIDE_X, NORMAL_POS_X);
            }
        } else {
            final double x1 = relX + span;
            final float uLeft = relX * textureScaleX + scrollX;
            final float uRight = (relX + span) * textureScaleX + scrollX;
            final float vCentre = (relZ + 0.5F) * textureScaleZ + scrollZ;
            if (dir == DIR_NORTH) {
                emitQuad(x0, deckTopY, z0, uLeft, vCentre, x1, deckTopY, z0, uRight, vCentre,
                    x1, 0.0, z0, uRight, vCentre, x0, 0.0, z0, uLeft, vCentre, FACE_SIDE_Z, NORMAL_NEG_Z);
            } else {
                final double zSouth = relZ + 1 - EDGE_OVERLAP;
                emitQuad(x0, 0.0, zSouth, uLeft, vCentre, x1, 0.0, zSouth, uRight, vCentre,
                    x1, deckTopY, zSouth, uRight, vCentre, x0, deckTopY, zSouth, uLeft, vCentre, FACE_SIDE_Z, NORMAL_POS_Z);
            }
        }
    }

    private void emitPlateRect(int relX0, int relZ0, int relX1, int relZ1, boolean emitUnderside, boolean emitTopSurface,
                               double plateTopY, float scrollX, float scrollZ, int bytesPerRect) {
        final double x0 = relX0, x1 = relX1, z0 = relZ0, z1 = relZ1;
        final float uLeft = relX0 * textureScaleX + scrollX;
        final float uRight = relX1 * textureScaleX + scrollX;
        final float vNorth = relZ0 * textureScaleZ + scrollZ;
        final float vSouth = relZ1 * textureScaleZ + scrollZ;

        ensureEmitCapacity(bytesPerRect);
        if (emitUnderside) {
            emitQuad(x0, 0.0, z0, uLeft, vNorth, x1, 0.0, z0, uRight, vNorth,
                x1, 0.0, z1, uRight, vSouth, x0, 0.0, z1, uLeft, vSouth, FACE_DOWN, NORMAL_NEG_Y);
        }
        if (emitTopSurface) {
            emitQuad(x0, plateTopY, z1, uLeft, vSouth, x1, plateTopY, z1, uRight, vSouth,
                x1, plateTopY, z0, uRight, vNorth, x0, plateTopY, z0, uLeft, vNorth, FACE_UP, NORMAL_POS_Y);
        }
    }

    private void addInteriorFaces(CloudShape shape, int anchorX, int anchorZ, double deckTopY, float scrollX, float scrollZ) {
        if ((shape.cellFlagsAt(anchorX, anchorZ) & CloudShape.OPAQUE) == 0) return;

        nextQuadDir = DIR_NONE;
        nextQuadGroup = GROUP_ALWAYS_DRAWN;
        nextQuadWedge = 0;
        nextQuadCellX = 0;
        nextQuadCellZ = 0;

        final float uLeft = scrollX;
        final float uRight = textureScaleX + scrollX;
        final float uCentre = 0.5F * textureScaleX + scrollX;
        final float vNorth = scrollZ;
        final float vSouth = textureScaleZ + scrollZ;
        final float vCentre = 0.5F * textureScaleZ + scrollZ;

        final double x0 = 0.0;
        final double x1 = 1.0;
        final double z0 = 0.0;
        final double z1 = 1.0;
        final double yBottom = 0.0;
        final double yTop = deckTopY;

        ensureEmitCapacity(6 * quadBytes);
        emitQuad(x0, yBottom, z1, uLeft, vSouth, x1, yBottom, z1, uRight, vSouth, x1, yBottom, z0, uRight, vNorth, x0, yBottom, z0, uLeft, vNorth, FACE_DOWN, NORMAL_POS_Y);
        emitQuad(x0, yTop, z0, uLeft, vNorth, x1, yTop, z0, uRight, vNorth, x1, yTop, z1, uRight, vSouth, x0, yTop, z1, uLeft, vSouth, FACE_UP, NORMAL_NEG_Y);
        emitQuad(x0, yBottom, z0, uCentre, vNorth, x0, yTop, z0, uCentre, vNorth, x0, yTop, z1, uCentre, vSouth, x0, yBottom, z1, uCentre, vSouth, FACE_SIDE_X, NORMAL_POS_X);
        emitQuad(x1, yBottom, z1, uCentre, vSouth, x1, yTop, z1, uCentre, vSouth, x1, yTop, z0, uCentre, vNorth, x1, yBottom, z0, uCentre, vNorth, FACE_SIDE_X, NORMAL_NEG_X);
        emitQuad(x0, yBottom, z0, uLeft, vCentre, x1, yBottom, z0, uRight, vCentre, x1, yTop, z0, uRight, vCentre, x0, yTop, z0, uLeft, vCentre, FACE_SIDE_Z, NORMAL_POS_Z);
        emitQuad(x0, yTop, z1, uLeft, vCentre, x1, yTop, z1, uRight, vCentre, x1, yBottom, z1, uRight, vCentre, x0, yBottom, z1, uLeft, vCentre, FACE_SIDE_Z, NORMAL_NEG_Z);
    }

    private void finishGeometry() {
        final int totalBytes = (int) (writeAddr - emitBufferAddr);
        final int vertices = totalBytes / vertexStride;
        pendingVertices = vertices;
        if (vertices == 0) {
            pendingUpload = null;
            return;
        }
        if (forPackShader) {
            pendingUpload = sortQuadsByFace(totalBytes);
        } else {
            emitBuffer.position(0).limit(totalBytes);
            pendingUpload = emitBuffer;
        }
    }

    void discardPendingUpload() {
        pendingVertices = -1;
        pendingUpload = null;
        uploadCursorBytes = -1;
    }

    void uploadBuilt() {
        if (beginUpload()) uploadSlice(Integer.MAX_VALUE);
    }

    boolean beginUpload() {
        final int vertices = pendingVertices;
        pendingVertices = -1;
        if (vertices <= 0) {
            pendingUpload = null;
            if (vao != null) {
                vao.delete();
                vao = null;
            }
            vboCapacityVertices = 0;
            vertexCount = 0;
            return false;
        }

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_UPLOAD);
        try {
            final boolean wantUv = vertexStride == STRIDE_WITH_UV;
            if (vao != null && vaoHasUv != wantUv) {
                vao.delete();
                vao = null;
                vboCapacityVertices = 0;
            }

            uploadVertices = vertices;
            uploadTotalBytes = vertices * vertexStride;

            if (vao != null && vertices <= vboCapacityVertices) {
                uploadCursorBytes = 0;
                return true;
            }

            final ByteBuffer source = pendingUpload;
            final int roomyVertices = clamp(vertices, vertices + (vertices >> 1), source.capacity() / vertexStride);
            source.position(0).limit(roomyVertices * vertexStride);
            if (vao == null) {
                vao = VertexBufferType.MUTABLE_RESIZABLE.allocate(
                    wantUv ? DefaultVertexFormat.POSITION_TEXTURE_NORMAL : DefaultVertexFormat.POSITION_NORMAL,
                    GL11.GL_TRIANGLES, source, roomyVertices);
                vaoHasUv = wantUv;
                setupShadeAttrib();
            } else {
                vao.getVBO().allocate(source, roomyVertices);
            }
            vboCapacityVertices = roomyVertices;
            uploadCursorBytes = uploadTotalBytes;
            return true;
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
    }

    boolean uploadSlice(int maxBytes) {
        if (uploadCursorBytes < 0) return true;
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_UPLOAD);
        try {
            if (uploadCursorBytes < uploadTotalBytes) {
                final int sliceBytes = Math.min(maxBytes, uploadTotalBytes - uploadCursorBytes);
                final ByteBuffer source = pendingUpload;
                source.position(uploadCursorBytes).limit(uploadCursorBytes + sliceBytes);
                vao.getVBO().update(source, uploadCursorBytes);
                uploadCursorBytes += sliceBytes;
                if (uploadCursorBytes < uploadTotalBytes) return false;
            }
            finishUpload();
            return true;
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
    }

    private void finishUpload() {
        uploadCursorBytes = -1;
        pendingUpload = null;
        vertexCount = uploadVertices;
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_EBO);
        ensureEbo(uploadVertices / 4);
        if (Tracy.FINE_ZONES) Tracy.endZone();
        orderAnchorX = Integer.MIN_VALUE;
        orderAnchorZ = Integer.MIN_VALUE;
    }

    private void setupShadeAttrib() {
        vao.bind();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vao.getVBO().getId());
        GLStateManager.glEnableVertexAttribArray(FACE_SHADE_ATTRIB);
        GLStateManager.glVertexAttribPointer(FACE_SHADE_ATTRIB, 1, GL11.GL_UNSIGNED_BYTE, true, vertexStride, normalOffset + 3);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        vao.unbind();
    }

    boolean orderStale(int anchorX, int anchorZ) {
        return anchorX != orderAnchorX || anchorZ != orderAnchorZ;
    }

    boolean orderPublished() {
        return orderAnchorX != Integer.MIN_VALUE;
    }

    void reorder(int anchorX, int anchorZ) {
        final int quads = vertexCount / 4;
        if (quads == 0) return;
        if (quads > quadCell.length) return;
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_REORDER);

        final int dx = anchorX - buildAnchorX;
        final int dz = anchorZ - buildAnchorZ;
        final int bands = (buildRadiusCells + 2) * ORDER_BANDS_PER_CELL;
        final int bucketCount = ORDER_GROUP_COUNT * bands;

        if (orderBucketCount.length < bucketCount + 1) orderBucketCount = new int[bucketCount + 1];
        else Arrays.fill(orderBucketCount, 0, bucketCount + 1, 0);

        if (quadBucket.length < quads) quadBucket = new int[quads];

        for (int q = 0; q < quads; q++) {
            final int cell = quadCell[q];
            final int cx = (short) (cell >> 16) - dx;
            final int cz = (short) cell - dz;
            final int groupBits = quadGroupBits[q];
            final int group = (groupBits >> 5) & 3;
            final int wedge = groupBits & 31;

            final boolean facingAway = group == GROUP_WALL_WEDGE && switch ((groupBits >> 7) & 7) {
                case DIR_WEST -> cx < 1;
                case DIR_EAST -> cx > -1;
                case DIR_NORTH -> cz < 1;
                case DIR_SOUTH -> cz > -1;
                default -> false;
            };

            int band = (int) (Math.sqrt((double) cx * cx + (double) cz * cz) * ORDER_BANDS_PER_CELL);
            if (band >= bands) band = bands - 1;

            final int groupSlot = facingAway ? 2 + 2 * WEDGE_COUNT : switch (group) {
                case GROUP_PLATE_ALWAYS -> 0;
                case GROUP_PLATE_WEDGE -> 1 + wedge;
                case GROUP_ALWAYS_DRAWN -> 1 + WEDGE_COUNT;
                default -> 2 + WEDGE_COUNT + wedge;
            };
            final int bucket = groupSlot * bands + band;
            quadBucket[q] = bucket;
            orderBucketCount[bucket]++;
        }

        int acc = 0;
        for (int b = 0; b < bucketCount; b++) {
            final int count = orderBucketCount[b];
            orderBucketCount[b] = acc;
            acc += count;
        }
        orderBucketCount[bucketCount] = acc;

        for (int w = 0; w < WEDGE_COUNT; w++) plateStartScratch[w] = orderBucketCount[(1 + w) * bands];
        plateStartScratch[WEDGE_COUNT] = orderBucketCount[(1 + WEDGE_COUNT) * bands];
        for (int w = 0; w < WEDGE_COUNT; w++) wallStartScratch[w] = orderBucketCount[(2 + WEDGE_COUNT + w) * bands];
        wallStartScratch[WEDGE_COUNT] = orderBucketCount[(2 + 2 * WEDGE_COUNT) * bands];

        final int needed = quads * 6;
        if (orderIndexBuffer == null || orderIndexBuffer.capacity() < needed) {
            orderIndexBuffer = BufferUtils.createIntBuffer(Math.max(needed, 4096));
        }

        if (orderIndexScratch.length < needed) orderIndexScratch = new int[needed];
        final int[] out = orderIndexScratch;
        for (int q = 0; q < quads; q++) {
            final int slot = orderBucketCount[quadBucket[q]]++;
            final int v = q * 4;
            final int base = slot * 6;
            out[base] = v;
            out[base + 1] = v + 1;
            out[base + 2] = v + 2;
            out[base + 3] = v;
            out[base + 4] = v + 2;
            out[base + 5] = v + 3;
        }

        final IntBuffer indices = orderIndexBuffer;
        indices.clear();
        indices.put(out, 0, needed);
        indices.flip();

        orderPendingQuads = quads;
        orderPendingAnchorX = anchorX;
        orderPendingAnchorZ = anchorZ;

        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    void reorderUpload() {
        if (orderPendingQuads <= 0) return;
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_REORDER_UPLOAD);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, orderIndexBuffer, GL15.GL_DYNAMIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        System.arraycopy(plateStartScratch, 0, plateStart, 0, plateStart.length);
        System.arraycopy(wallStartScratch, 0, wallStart, 0, wallStart.length);
        eboQuadCapacity = orderPendingQuads;
        eboIsSequential = false;
        orderAnchorX = orderPendingAnchorX;
        orderAnchorZ = orderPendingAnchorZ;
        orderPendingQuads = 0;
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    void discardPendingReorder() {
        orderPendingQuads = 0;
    }

    private void ensureEbo(int quads) {
        if (eboId == -1) {
            eboId = GLStateManager.glGenBuffers();
        }

        if (quads <= eboQuadCapacity && eboIsSequential) return;
        eboIsSequential = true;
        final int capacity = Math.max(quads + (quads >> 1), 4096);
        final IntBuffer indices = sequentialIndices(capacity);
        indices.position(0).limit(capacity * 6);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        eboQuadCapacity = capacity;
    }

    private static IntBuffer sequentialIndices(int quads) {
        if (sharedSequentialIndices != null && sharedSequentialQuads >= quads) return sharedSequentialIndices;

        final IntBuffer indices = BufferUtils.createIntBuffer(quads * 6);
        for (int q = 0; q < quads; q++) {
            final int v = q * 4;
            indices.put(v).put(v + 1).put(v + 2);
            indices.put(v).put(v + 2).put(v + 3);
        }
        indices.flip();
        sharedSequentialIndices = indices;
        sharedSequentialQuads = quads;
        return indices;
    }

    void deleteEbo() {
        if (eboId != -1) {
            GLStateManager.glDeleteBuffers(eboId);
            eboId = -1;
            eboQuadCapacity = 0;
        }
    }

    void deleteVao() {
        if (vao != null) {
            vao.delete();
            vao = null;
        }
        vboCapacityVertices = 0;
        vertexCount = 0;
    }

    void bind() {
        vao.bind();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
    }

    void unbind() {
        vao.unbind();
    }

    void drawRange(int startQuad, int endQuad) {
        final int quads = endQuad - startQuad;
        if (quads <= 0) return;
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, quads * 6, GL11.GL_UNSIGNED_INT, (long) startQuad * 6L * 4L);
    }

    void drawAll() {
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, (vertexCount / 4) * 6, GL11.GL_UNSIGNED_INT, 0L);
    }

    boolean faceEmpty(int face) {
        final int base = face * WEDGE_BUCKETS;
        return faceWedgeStart[base + WEDGE_BUCKETS] == faceWedgeStart[base];
    }

    void drawFace(int face) {
        final int base = face * WEDGE_BUCKETS;
        drawRange(faceWedgeStart[base], faceWedgeStart[base + WEDGE_BUCKETS]);
    }

    private void prepareEmitBuffer() {
        if (emitBuffer == null) {
            emitBufferCapacity = 256 * 1024;
            emitBuffer = MemoryUtilities.memRealloc((ByteBuffer) null, emitBufferCapacity);
            emitBufferAddr = MemoryUtilities.memAddress(emitBuffer, 0);
        }

        emitBuffer.clear();
        writeAddr = emitBufferAddr;
        writeEnd = emitBufferAddr + emitBufferCapacity;
        quadCount = 0;
    }

    private void growEmitBuffer(int neededBytes) {
        final long oldBytes = writeAddr - emitBufferAddr;
        int newCap = emitBufferCapacity;
        while (newCap < neededBytes) newCap *= 2;
        emitBuffer = MemoryUtilities.memRealloc(emitBuffer, newCap);
        emitBufferAddr = MemoryUtilities.memAddress(emitBuffer, 0);
        emitBufferCapacity = newCap;
        writeAddr = emitBufferAddr + oldBytes;
        writeEnd = emitBufferAddr + newCap;
    }

    private void ensureEmitCapacity(int bytes) {
        if (writeAddr + bytes > writeEnd) {
            growEmitBuffer((int) (writeAddr - emitBufferAddr) + bytes);
        }
    }

    private void emitQuad(double x0, double y0, double z0, float u0, float v0, double x1, double y1, double z1, float u1, float v1, double x2, double y2, double z2, float u2, float v2, double x3, double y3, double z3, float u3, float v3, byte face, int packedNormal) {
        if (quadCount == quadFace.length) {
            quadFace = Arrays.copyOf(quadFace, quadFace.length * 2);
            if (!forPackShader) {
                quadCell = Arrays.copyOf(quadCell, quadCell.length * 2);
                quadGroupBits = Arrays.copyOf(quadGroupBits, quadGroupBits.length * 2);
            }
        }
        if (!forPackShader) {
            quadCell[quadCount] = (nextQuadCellX << 16) | (nextQuadCellZ & 0xFFFF);
            quadGroupBits[quadCount] = (short) ((nextQuadDir << 7) | (nextQuadGroup << 5) | nextQuadWedge);
        }
        quadFace[quadCount++] = face;

        final int normalAndShade = packedNormal | FACE_SHADE_PACKED[face];
        long addr = writeAddr;
        addr = writeVertex(addr, x0, y0, z0, u0, v0, normalAndShade);
        addr = writeVertex(addr, x1, y1, z1, u1, v1, normalAndShade);
        addr = writeVertex(addr, x2, y2, z2, u2, v2, normalAndShade);
        addr = writeVertex(addr, x3, y3, z3, u3, v3, normalAndShade);
        writeAddr = addr;
    }

    private long writeVertex(long addr, double x, double y, double z, float u, float v, int normalAndShade) {
        MemoryUtilities.memPutFloat(addr, (float) x);
        MemoryUtilities.memPutFloat(addr + 4, (float) y);
        MemoryUtilities.memPutFloat(addr + 8, (float) z);
        if (normalOffset == NORMAL_OFFSET_WITH_UV) {
            MemoryUtilities.memPutFloat(addr + 12, u);
            MemoryUtilities.memPutFloat(addr + 16, v);
        }
        MemoryUtilities.memPutInt(addr + normalOffset, normalAndShade);
        return addr + vertexStride;
    }

    private ByteBuffer sortQuadsByFace(int emittedBytes) {
        final int quadTotal = quadCount;
        Arrays.fill(faceWedgeStart, 0);

        if (quadSortKey.length < quadTotal) quadSortKey = new int[quadTotal];
        final byte[] faces = quadFace;
        boolean alreadyGrouped = true;
        int previousKey = 0;
        for (int q = 0; q < quadTotal; q++) {
            final int key = faces[q] * WEDGE_BUCKETS;
            if (key < previousKey) alreadyGrouped = false;
            previousKey = key;
            quadSortKey[q] = key;
            faceWedgeStart[key]++;
        }

        int acc = 0;
        for (int b = 0; b < FACE_WEDGE_BUCKETS; b++) {
            final int count = faceWedgeStart[b];
            faceWedgeStart[b] = acc;
            faceWedgeCursor[b] = acc;
            acc += count;
        }
        faceWedgeStart[FACE_WEDGE_BUCKETS] = acc;

        if (alreadyGrouped) {
            emitBuffer.position(0).limit(emittedBytes);
            return emitBuffer;
        }

        final int totalBytes = quadTotal * quadBytes;
        if (sortedBuffer == null || sortedBufferCapacity < totalBytes) {
            int rounded = Math.max(totalBytes, 64 * 1024);
            int pow2 = Integer.highestOneBit(rounded - 1) << 1;
            if (pow2 <= 0) pow2 = rounded;
            sortedBuffer = MemoryUtilities.memRealloc(sortedBuffer, pow2);
            sortedBufferAddr = MemoryUtilities.memAddress(sortedBuffer, 0);
            sortedBufferCapacity = pow2;
        }

        final long srcBase = emitBufferAddr;
        int q = 0;
        while (q < quadTotal) {
            final int key = quadSortKey[q];
            int runEnd = q + 1;
            while (runEnd < quadTotal && quadSortKey[runEnd] == key) runEnd++;
            final int runLen = runEnd - q;
            final long runBytes = (long) runLen * quadBytes;
            MemoryUtilities.memCopy(srcBase + (long) q * quadBytes,
                sortedBufferAddr + (long) faceWedgeCursor[key] * quadBytes, runBytes);
            faceWedgeCursor[key] += runLen;
            q = runEnd;
        }

        sortedBuffer.position(0).limit(totalBytes);
        return sortedBuffer;
    }
}
