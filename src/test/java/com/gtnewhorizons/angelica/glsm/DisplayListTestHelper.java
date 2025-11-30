package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
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
        return new AccumulatedDraw(quads, transform, flags, commandIndex);
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
    public static AccumulatedDraw createDrawWithTransform(Matrix4f transform, int commandIndex) {
        return createDraw(
            Collections.singletonList(createSimpleQuad()),
            transform,
            positionOnly(),
            commandIndex
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
}
