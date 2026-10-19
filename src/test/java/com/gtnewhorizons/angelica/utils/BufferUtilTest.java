package com.gtnewhorizons.angelica.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BufferUtil.
 */
class BufferUtilTest {

    @ParameterizedTest(name = "from position {0}")
    @ValueSource(ints = {0, 1})
    void testCopyDirectBufferCopiesRemainingData(int start) {
        FloatBuffer src = memAllocFloat(4);
        src.put(1.0f).put(2.0f).put(3.0f).put(4.0f);
        src.flip();
        src.position(start);

        FloatBuffer dst = BufferUtil.copyDirectBuffer(src);
        assertTrue(dst.isDirect(), "Returned buffer should be direct");

        try {
            assertEquals(start, src.position(), "Source position should be preserved");
            assertEquals(4 - start, dst.remaining(), "Should copy remaining elements only");
            for (int i = 0; i < 4 - start; i++) {
                assertEquals(start + 1 + i, dst.get(i), 0.0001f);
            }
        } finally {
            memFree(src);
            memFree(dst);
        }
    }

}
