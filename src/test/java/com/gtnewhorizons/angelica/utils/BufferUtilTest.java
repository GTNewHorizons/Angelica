package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BufferUtil.
 */
@ExtendWith(AngelicaExtension.class)
class BufferUtilTest {

    @Test
    void testCopyDirectBufferCopiesData() {
        FloatBuffer src = memAllocFloat(4);
        src.put(1.0f).put(2.0f).put(3.0f).put(4.0f);
        src.flip();

        FloatBuffer dst = BufferUtil.copyDirectBuffer(src);
        assertTrue(dst.isDirect(), "Returned buffer should be direct");

        try {
            assertEquals(4, dst.remaining());
            assertEquals(1.0f, dst.get(0), 0.0001f);
            assertEquals(2.0f, dst.get(1), 0.0001f);
            assertEquals(3.0f, dst.get(2), 0.0001f);
            assertEquals(4.0f, dst.get(3), 0.0001f);
        } finally {
            memFree(src);
            memFree(dst);
        }
    }

    @Test
    void testCopyDirectBufferPreservesSourcePosition() {
        FloatBuffer src = memAllocFloat(4);
        src.put(1.0f).put(2.0f).put(3.0f).put(4.0f);
        src.flip();
        src.position(1); // Start at second element

        int posBefore = src.position();
        FloatBuffer dst = BufferUtil.copyDirectBuffer(src);

        try {
            assertEquals(posBefore, src.position(), "Source position should be preserved");
            assertEquals(3, dst.remaining(), "Should copy remaining elements only");
            assertEquals(2.0f, dst.get(0), 0.0001f);
            assertEquals(3.0f, dst.get(1), 0.0001f);
            assertEquals(4.0f, dst.get(2), 0.0001f);
        } finally {
            memFree(src);
            memFree(dst);
        }
    }

}
