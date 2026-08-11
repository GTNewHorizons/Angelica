package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Off-heap SectionRenderData rows in the FULL layout. Callers own the buffer and must memFree it. */
final class SyntheticSectionData {

    private static final int BYTES = 92;
    private static final int FACINGS = 7;
    private static final int[] ZEROS = new int[FACINGS];

    private SyntheticSectionData() {}

    static ByteBuffer row(int sliceMaskLow, int sliceMaskHigh, int[] vertexOffsets, int[] elementCounts, int[] indexOffsets) {
        final ByteBuffer buf = MemoryUtilities.memAlloc(BYTES).order(ByteOrder.nativeOrder());
        buf.putInt(0, sliceMaskLow);
        buf.putInt(4, sliceMaskHigh);
        for (int facing = 0; facing < FACINGS; facing++) {
            final int off = 8 + facing * 12;
            buf.putInt(off + 0, vertexOffsets[facing]);
            buf.putInt(off + 4, elementCounts[facing]);
            buf.putInt(off + 8, indexOffsets[facing]);
        }
        return buf;
    }

    static ByteBuffer sliceMaskOnly(int sliceMask) {
        return row(sliceMask, 0, ZEROS, ZEROS, ZEROS);
    }

    static ByteBuffer singleQuadOnFacing0() {
        final int[] vertexOffsets = { 0, 4, 4, 4, 4, 4, 4 };
        final int[] elementCounts = { 6, 0, 0, 0, 0, 0, 0 };
        return row(0x01, 0, vertexOffsets, elementCounts, ZEROS);
    }
}
