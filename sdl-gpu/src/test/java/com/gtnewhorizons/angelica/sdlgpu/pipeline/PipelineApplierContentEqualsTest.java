package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineApplierContentEqualsTest {

    private static FloatBuffer direct(float... values) {
        final FloatBuffer buf = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(values).flip();
        return buf;
    }

    @Test
    void identicalContentMatches() {
        final float[] existing = {1f, 2f, 3f, 30000f};
        assertTrue(PipelineApplier.contentEquals(existing, direct(1f, 2f, 3f, 30000f), 0, 4));
    }

    @Test
    void differingElementMismatches() {
        final float[] existing = {1f, 2f, 3f, 4f};
        assertFalse(PipelineApplier.contentEquals(existing, direct(1f, 2f, 3.5f, 4f), 0, 4));
        assertFalse(PipelineApplier.contentEquals(existing, direct(0f, 2f, 3f, 4f), 0, 4));
        assertFalse(PipelineApplier.contentEquals(existing, direct(1f, 2f, 3f, 5f), 0, 4));
    }

    @Test
    void positionOffsetIsHonored() {
        final float[] existing = {7f, 8f};
        final FloatBuffer buf = direct(0f, 7f, 8f);
        buf.position(1);
        assertTrue(PipelineApplier.contentEquals(existing, buf, buf.position(), 2));
    }

    @Test
    void nanBitPatternsCompareEqual() {
        final float nan = Float.intBitsToFloat(0x7FC00001);
        final float[] existing = {nan, 1f};
        assertTrue(PipelineApplier.contentEquals(existing, direct(nan, 1f), 0, 2));
        assertFalse(PipelineApplier.contentEquals(existing, direct(Float.NaN, 1f), 0, 2));
    }

    @Test
    void signedZeroIsAMismatch() {
        final float[] existing = {0.0f};
        assertFalse(PipelineApplier.contentEquals(existing, direct(-0.0f), 0, 1));
    }
}
