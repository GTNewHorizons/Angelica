package com.gtnewhorizons.angelica.client.font;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontBatchMatrixTest {

    private static Matrix4f overlayOrtho() {
        return new Matrix4f().ortho(0, 854, 480, 0, 1000, 3000).translate(0, 0, -2000);
    }

    @Test
    void anUnchangedMatrixDoesNotSegment() {
        final Matrix4f cached = overlayOrtho();
        assertTrue(cached.equals(new Matrix4f(cached), 0.0f));
    }

    @Test
    void aScaleSegments() {
        final Matrix4f cached = overlayOrtho();
        assertFalse(cached.equals(new Matrix4f(cached).scale(0.5f), 0.0f));
    }

    @Test
    void aDepthOnlyTranslateSegments() {
        final Matrix4f cached = overlayOrtho();
        assertFalse(cached.equals(new Matrix4f(cached).translate(0, 0, 50), 0.0f));
    }

    @Test
    void aStackIsComparedByValue() {
        final Matrix4f cached = overlayOrtho();
        final Matrix4fStack live = new Matrix4fStack(4);
        live.set(cached);
        live.pushMatrix();
        assertTrue(cached.equals(live, 0.0f));

        live.scale(0.5f);
        assertFalse(cached.equals(live, 0.0f));

        live.popMatrix();
        assertTrue(cached.equals(live, 0.0f));
    }
}
