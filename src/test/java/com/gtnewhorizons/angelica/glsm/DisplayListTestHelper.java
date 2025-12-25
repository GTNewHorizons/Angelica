package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.line.ModelLine;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.tri.ModelTriangle;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedPrimitiveDraw;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for building test data for display list batching tests.
 * Allows creating quads and AccumulatedDraws without GL context.
 */
public final class DisplayListTestHelper {

    private DisplayListTestHelper() {}

    // ==================== Quad Builders ====================

    /**
     * Create a quad from 4 vertex positions.
     * @param vertices 4 vertices, each as [x, y, z]
     */
    public static ModelQuad createQuad(float[][] vertices) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Quad requires exactly 4 vertices");
        }

        ModelQuad quad = new ModelQuad();
        for (int i = 0; i < 4; i++) {
            quad.setX(i, vertices[i][0]);
            quad.setY(i, vertices[i][1]);
            quad.setZ(i, vertices[i][2]);
            // Set default color (white, fully opaque)
            quad.setColor(i, 0xFFFFFFFF);
            // Set default UVs
            quad.setTexU(i, i == 1 || i == 2 ? 1.0f : 0.0f);
            quad.setTexV(i, i >= 2 ? 1.0f : 0.0f);
        }
        return quad;
    }

    /**
     * Create a simple unit quad at origin (0,0,0) to (1,1,0).
     */
    public static ModelQuad createSimpleQuad() {
        return createQuad(new float[][] {
            {0, 0, 0},
            {1, 0, 0},
            {1, 1, 0},
            {0, 1, 0}
        });
    }

    /**
     * Create a quad at a specific position (offset from origin).
     */
    public static ModelQuad createQuadAt(float x, float y, float z) {
        return createQuad(new float[][] {
            {x, y, z},
            {x + 1, y, z},
            {x + 1, y + 1, z},
            {x, y + 1, z}
        });
    }

    /**
     * Create multiple simple quads.
     */
    public static List<ModelQuadViewMutable> createQuads(int count) {
        List<ModelQuadViewMutable> quads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            quads.add(createSimpleQuad());
        }
        return quads;
    }

    // ==================== AccumulatedDraw Builders ====================

    /**
     * Create an AccumulatedDraw with specified parameters.
     */
    public static AccumulatedDraw createDraw(
            List<ModelQuadViewMutable> quads,
            Matrix4f transform,
            CapturingTessellator.Flags flags,
            int commandIndex) {
        return new AccumulatedDraw(quads, transform, flags, commandIndex, 0);
    }

    public static AccumulatedDraw createDraw(
            List<ModelQuadViewMutable> quads,
            Matrix4f transform,
            CapturingTessellator.Flags flags,
            int commandIndex,
            int matrixGeneration) {
        return new AccumulatedDraw(quads, transform, flags, commandIndex, matrixGeneration, 0);
    }

    public static AccumulatedDraw createDraw(
            List<ModelQuadViewMutable> quads,
            Matrix4f transform,
            CapturingTessellator.Flags flags,
            int commandIndex,
            int matrixGeneration,
            int stateGeneration) {
        return new AccumulatedDraw(quads, transform, flags, commandIndex, matrixGeneration, stateGeneration);
    }

    /**
     * Create an AccumulatedDraw with a single quad and identity transform.
     */
    public static AccumulatedDraw createSimpleDraw(int commandIndex) {
        return createDraw(
            Collections.singletonList(createSimpleQuad()),
            new Matrix4f(),
            positionOnly(),
            commandIndex
        );
    }

    /**
     * Create an AccumulatedDraw with specified transform.
     */
    public static AccumulatedDraw createDrawWithTransform(Matrix4f transform, int commandIndex, int matrixGeneration) {
        return createDraw(
            Collections.singletonList(createSimpleQuad()),
            transform,
            positionOnly(),
            commandIndex,
            matrixGeneration
        );
    }

    /**
     * Create an AccumulatedDraw with specified transform and stateGeneration.
     */
    public static AccumulatedDraw createDrawWithTransformAndBatch(Matrix4f transform, int commandIndex, int matrixGeneration, int stateGeneration) {
        return createDraw(
            Collections.singletonList(createSimpleQuad()),
            transform,
            positionOnly(),
            commandIndex,
            matrixGeneration,
            stateGeneration
        );
    }

    /**
     * Create an AccumulatedDraw with specified flags.
     */
    public static AccumulatedDraw createDrawWithFlags(CapturingTessellator.Flags flags, int commandIndex) {
        return createDraw(
            Collections.singletonList(createSimpleQuad()),
            new Matrix4f(),
            flags,
            commandIndex
        );
    }

    /**
     * Create an AccumulatedDraw with multiple quads.
     */
    public static AccumulatedDraw createDrawWithQuads(int quadCount, int commandIndex) {
        return createDraw(
            createQuads(quadCount),
            new Matrix4f(),
            positionOnly(),
            commandIndex
        );
    }

    // ==================== Flag Helpers ====================

    /**
     * Flags for position-only vertices (no color, texture, brightness, normals).
     */
    public static CapturingTessellator.Flags positionOnly() {
        return new CapturingTessellator.Flags(false, false, false, false);
    }

    /**
     * Flags for position + color vertices.
     */
    public static CapturingTessellator.Flags positionColor() {
        return new CapturingTessellator.Flags(false, false, true, false);
    }

    /**
     * Flags for position + texture vertices.
     */
    public static CapturingTessellator.Flags positionTexture() {
        return new CapturingTessellator.Flags(true, false, false, false);
    }

    /**
     * Flags for position + color + texture vertices.
     */
    public static CapturingTessellator.Flags positionColorTexture() {
        return new CapturingTessellator.Flags(true, false, true, false);
    }

    /**
     * Flags for full vertex format (all attributes).
     */
    public static CapturingTessellator.Flags fullFormat() {
        return new CapturingTessellator.Flags(true, true, true, true);
    }

    // ==================== Transform Helpers ====================

    /**
     * Create identity transform.
     */
    public static Matrix4f identity() {
        return new Matrix4f();
    }

    /**
     * Create translation transform.
     */
    public static Matrix4f translate(float x, float y, float z) {
        return new Matrix4f().translate(x, y, z);
    }

    /**
     * Create rotation transform (around Y axis).
     */
    public static Matrix4f rotateY(float degrees) {
        return new Matrix4f().rotateY((float) Math.toRadians(degrees));
    }

    /**
     * Create scale transform.
     */
    public static Matrix4f scale(float s) {
        return new Matrix4f().scale(s);
    }

    // ==================== Primitive Builders (Lines, Triangles) ====================

    /**
     * Create a ModelLine from two vertex positions.
     */
    public static ModelLine createLine(float x1, float y1, float z1, float x2, float y2, float z2) {
        ModelLine line = new ModelLine();
        line.setX(0, x1);
        line.setY(0, y1);
        line.setZ(0, z1);
        line.setColor(0, 0xFFFFFFFF);
        line.setTexU(0, 0.0f);
        line.setTexV(0, 0.0f);
        line.setLight(0, 0);
        line.setForgeNormal(0, 0);

        line.setX(1, x2);
        line.setY(1, y2);
        line.setZ(1, z2);
        line.setColor(1, 0xFFFFFFFF);
        line.setTexU(1, 1.0f);
        line.setTexV(1, 1.0f);
        line.setLight(1, 0);
        line.setForgeNormal(1, 0);
        return line;
    }

    /**
     * Create a simple unit line from (0,0,0) to (1,0,0).
     */
    public static ModelLine createSimpleLine() {
        return createLine(0, 0, 0, 1, 0, 0);
    }

    /**
     * Create multiple simple lines.
     */
    public static List<ModelLine> createLines(int count) {
        List<ModelLine> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(createLine(i, 0, 0, i + 1, 0, 0));
        }
        return lines;
    }

    /**
     * Create a ModelTriangle from three vertex positions.
     */
    public static ModelTriangle createTriangle(float x1, float y1, float z1,
                                                float x2, float y2, float z2,
                                                float x3, float y3, float z3) {
        ModelTriangle tri = new ModelTriangle();
        tri.setX(0, x1);
        tri.setY(0, y1);
        tri.setZ(0, z1);
        tri.setColor(0, 0xFFFFFFFF);
        tri.setTexU(0, 0.0f);
        tri.setTexV(0, 0.0f);
        tri.setLight(0, 0);
        tri.setForgeNormal(0, 0);

        tri.setX(1, x2);
        tri.setY(1, y2);
        tri.setZ(1, z2);
        tri.setColor(1, 0xFFFFFFFF);
        tri.setTexU(1, 1.0f);
        tri.setTexV(1, 0.0f);
        tri.setLight(1, 0);
        tri.setForgeNormal(1, 0);

        tri.setX(2, x3);
        tri.setY(2, y3);
        tri.setZ(2, z3);
        tri.setColor(2, 0xFFFFFFFF);
        tri.setTexU(2, 0.5f);
        tri.setTexV(2, 1.0f);
        tri.setLight(2, 0);
        tri.setForgeNormal(2, 0);
        return tri;
    }

    /**
     * Create a simple unit triangle at origin.
     */
    public static ModelTriangle createSimpleTriangle() {
        return createTriangle(0, 0, 0, 1, 0, 0, 0.5f, 1, 0);
    }

    /**
     * Create multiple simple triangles.
     */
    public static List<ModelTriangle> createTriangles(int count) {
        List<ModelTriangle> triangles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            triangles.add(createTriangle(i, 0, 0, i + 1, 0, 0, i + 0.5f, 1, 0));
        }
        return triangles;
    }

    /**
     * Create a mixed list of primitives (lines and triangles).
     */
    public static List<ModelPrimitiveView> createMixedPrimitives(int lineCount, int triangleCount) {
        List<ModelPrimitiveView> primitives = new ArrayList<>(lineCount + triangleCount);
        for (int i = 0; i < lineCount; i++) {
            primitives.add(createLine(i, 0, 0, i + 1, 0, 0));
        }
        for (int i = 0; i < triangleCount; i++) {
            primitives.add(createTriangle(i, 1, 0, i + 1, 1, 0, i + 0.5f, 2, 0));
        }
        return primitives;
    }

    // ==================== AccumulatedPrimitiveDraw Builders ====================

    /**
     * Create an AccumulatedPrimitiveDraw with specified parameters.
     */
    public static AccumulatedPrimitiveDraw createPrimitiveDraw(
            List<ModelPrimitiveView> primitives,
            Matrix4f transform,
            CapturingTessellator.Flags flags,
            int commandIndex) {
        return new AccumulatedPrimitiveDraw(primitives, transform, flags, commandIndex, 0);
    }

    public static AccumulatedPrimitiveDraw createPrimitiveDraw(
            List<ModelPrimitiveView> primitives,
            Matrix4f transform,
            CapturingTessellator.Flags flags,
            int commandIndex,
            int matrixGeneration) {
        return new AccumulatedPrimitiveDraw(primitives, transform, flags, commandIndex, matrixGeneration);
    }

    /**
     * Create an AccumulatedPrimitiveDraw with lines only.
     */
    public static AccumulatedPrimitiveDraw createLinesDraw(int lineCount, int commandIndex) {
        List<ModelPrimitiveView> primitives = new ArrayList<>(lineCount);
        for (ModelLine line : createLines(lineCount)) {
            primitives.add(line);
        }
        return createPrimitiveDraw(primitives, identity(), positionOnly(), commandIndex);
    }

    /**
     * Create an AccumulatedPrimitiveDraw with triangles only.
     */
    public static AccumulatedPrimitiveDraw createTrianglesDraw(int triangleCount, int commandIndex) {
        List<ModelPrimitiveView> primitives = new ArrayList<>(triangleCount);
        for (ModelTriangle tri : createTriangles(triangleCount)) {
            primitives.add(tri);
        }
        return createPrimitiveDraw(primitives, identity(), positionOnly(), commandIndex);
    }

    /**
     * Create an AccumulatedPrimitiveDraw with mixed lines and triangles.
     */
    public static AccumulatedPrimitiveDraw createMixedPrimitiveDraw(int lineCount, int triangleCount, int commandIndex) {
        List<ModelPrimitiveView> primitives = createMixedPrimitives(lineCount, triangleCount);
        return createPrimitiveDraw(primitives, identity(), positionOnly(), commandIndex);
    }

    /**
     * Create an AccumulatedPrimitiveDraw with specified transform.
     */
    public static AccumulatedPrimitiveDraw createPrimitiveDrawWithTransform(
            List<ModelPrimitiveView> primitives, Matrix4f transform, int commandIndex) {
        return createPrimitiveDraw(primitives, transform, positionOnly(), commandIndex);
    }
}
