package org.embeddedt.embeddium.impl.render.chunk.sorting;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;

import java.util.BitSet;

public class TranslucentQuadAnalyzer {
    // X/Y/Z for each quad center
    private static final int EXPECTED_QUADS = 1000;
    private final FloatArrayList quadCenters = new FloatArrayList(EXPECTED_QUADS * 3);
    private final Vector3f[] vertexPositions = new Vector3f[4];
    private final Vector3f currentNormal = new Vector3f();
    private final Vector3f globalNormal = new Vector3f();
    private final BitSet normalSigns = new BitSet(EXPECTED_QUADS);
    private static final BitSet EMPTY = new BitSet();
    private int currentVertex;
    private boolean hasDistinctNormals;

    public enum Level {
        /**
         * No sorting is required of the current section.
         */
        NONE,
        /**
         * Sorting is required once during meshing.
         */
        STATIC,
        /**
         * Sorting is required any time the camera moves.
         */
        DYNAMIC;

        public static final Level[] VALUES = values();

        public boolean requiresDynamicSorting() {
            return this.ordinal() >= Level.DYNAMIC.ordinal();
        }
    }

    public TranslucentQuadAnalyzer() {
        for(int i = 0; i < 4; i++) {
            vertexPositions[i] = new Vector3f();
        }
    }

    public record SortState(Level level, float[] centers, int centersLength, BitSet normalSigns, Vector3f sharedNormal) {
        public static final SortState NONE = new SortState(Level.NONE, null, 0, null, null);

        public boolean requiresDynamicSorting() {
            return level.requiresDynamicSorting();
        }

        public SortState compactForStorage() {
            if(this == NONE || requiresDynamicSorting()) {
                return this;
            } else {
                return new SortState(level, null, 0, null, null);
            }
        }

        public static SortState compacted(SortState state) {
            return state != null ? state.compactForStorage() : null;
        }
    }

    private static BitSet cloneBits(BitSet bits) {
        if(bits.isEmpty()) {
            return EMPTY;
        } else {
            return (BitSet)bits.clone();
        }
    }

    private boolean areAllQuadsOnSamePlane() {
        // Let globalNormal = (a, b, c). Any plane with this normal vector is denoted by the equation ax + by + cz = d,
        // for some real number d.
        //
        // Next, we know that any quad has either globalNormal or -globalNormal as a normal vector. Suppose a quad q has center (x, y, z).
        // We define the "plane extension" of q as the unique plane in 3D space that q resides within. In particular,
        // any quad's plane extension (when all share parallel normals) is uniquely determined by the choice of d.
        //
        // If all quads are on the same plane, we don't need to sort at all. Otherwise, we need to use a static sort.
        // Recalling that d is given by ax + by + cz, and that we know all those variables for any quad, we can
        // easily determine if all quads reside in the same plane by computing this expression for each quad center,
        // and checking that we obtain at most one value.

        var centerArray = quadCenters.elements();

        float a = globalNormal.x, b = globalNormal.y, c = globalNormal.z;
        float d = a * centerArray[0] + b * centerArray[1] + c * centerArray[2];
        int nQuads = quadCenters.size() / 3;
        for(int quadIdx = 1; quadIdx < nQuads; quadIdx++) {
            int centerOff = quadIdx * 3;
            float candidateD = a * centerArray[centerOff + 0] + b * centerArray[centerOff + 1] + c * centerArray[centerOff + 2];
            if(Math.abs(candidateD - d) >= 1.0E-5F) {
                // Different planes
                return false;
            }
        }

        return true;
    }

    public SortState getSortState() {
        if(quadCenters.isEmpty()) {
            return SortState.NONE;
        } else {
            Level sortLevel;

            // Figure out what sort level is required
            if(hasDistinctNormals) {
                // Must use dynamic sort
                sortLevel = Level.DYNAMIC;
            } else {
                // If all quads are on the same plane we can use NONE sorting, otherwise we need to sort statically to put
                // them in the right order
                sortLevel = areAllQuadsOnSamePlane() ? Level.NONE : Level.STATIC;
            }

            SortState finalState;

            if (sortLevel == Level.NONE) {
                finalState = SortState.NONE;
            } else if (sortLevel.requiresDynamicSorting()) {
                // Clone everything
                finalState = new SortState(sortLevel, quadCenters.toArray(new float[0]), quadCenters.size(), cloneBits(normalSigns), new Vector3f(globalNormal));
            } else {
                // Just make a thin wrapper around our backing objects
                finalState = new SortState(sortLevel, quadCenters.elements(), quadCenters.size(), normalSigns, globalNormal);
            }

            return finalState;
        }
    }

    public void clear() {
        quadCenters.clear();
        currentVertex = 0;
        globalNormal.zero();
        normalSigns.clear();
        hasDistinctNormals = false;
    }

    private void calculateNormal() {
        final Vector3f v0 = vertexPositions[0];

        final float x0 = v0.x;
        final float y0 = v0.y;
        final float z0 = v0.z;

        final Vector3f v1 = vertexPositions[1];

        final float x1 = v1.x;
        final float y1 = v1.y;
        final float z1 = v1.z;

        final Vector3f v2 = vertexPositions[2];

        final float x2 = v2.x;
        final float y2 = v2.y;
        final float z2 = v2.z;

        final Vector3f v3 = vertexPositions[3];

        final float x3 = v3.x;
        final float y3 = v3.y;
        final float z3 = v3.z;

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        currentNormal.set(normX, normY, normZ);
    }

    private void captureQuad() {
        // The four positions in vertexPositions form a quad. Find its center
        float totalX = 0, totalY = 0, totalZ = 0;
        for (Vector3f vertex : vertexPositions) {
            totalX += vertex.x;
            totalY += vertex.y;
            totalZ += vertex.z;
        }

        var centers = quadCenters;
        int currentQuadIndex = centers.size() / 3;

        centers.ensureCapacity(centers.size() + 3);
        centers.add(totalX / 4);
        centers.add(totalY / 4);
        centers.add(totalZ / 4);

        if(!hasDistinctNormals) {
            calculateNormal();

            if(globalNormal.x == 0 && globalNormal.y == 0 && globalNormal.z == 0) {
                // No normal has been tracked thus far, choose this one
                globalNormal.set(currentNormal);
            } else {
                float dotProduct = globalNormal.dot(currentNormal);
                // Technically, only 1 and -1 imply that the quads share a normal. However, if the dot products
                // are very, very similar, we pretend they share a normal for optimization purposes. This is an
                // approximation that allows very slightly slanted water in edges of underwater lakes to be counted as
                // STATIC rather than DYNAMIC.
                if (Math.abs(dotProduct) >= 0.98) {
                    if (dotProduct < 0) {
                        // Flag this quad as being flipped relative to the global normal
                        normalSigns.set(currentQuadIndex);
                    }
                } else {
                    hasDistinctNormals = true;
                }
            }
        }
    }

    public void capture(ChunkVertexEncoder.Vertex vertex) {
        int i = currentVertex;
        vertexPositions[i].set(vertex.x, vertex.y, vertex.z);
        i++;
        if(i == 4) {
            captureQuad();
            i = 0;
        }
        currentVertex = i;
    }
}
