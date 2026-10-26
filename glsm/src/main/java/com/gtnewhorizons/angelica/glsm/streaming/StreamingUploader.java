package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;


public final class StreamingUploader {

    public enum UploadStrategy {
        BUFFER_DATA,
        BUFFER_SUB_DATA,
        MAP_BUFFER_RANGE
    }

    private static final int MAP_WRITE_INVALIDATE_BUFFER = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT;

    public static int upload(ByteBuffer data, int capacity) {
        return upload(GLStateManager.getInitConfig().getStreamingUploadStrategy(), data, capacity);
    }

    public static int upload(UploadStrategy strategy, ByteBuffer data, int capacity) {
        switch (strategy) {
            case BUFFER_DATA -> {
                GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                return data.remaining();
            }
            case BUFFER_SUB_DATA -> {
                if (data.remaining() > capacity) {
                    GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                GLSMBuffers.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
                return capacity;
            }
            case MAP_BUFFER_RANGE -> {
                if (data.remaining() > capacity) {
                    GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                final int dataSize = data.remaining();
                final long dst = GLSMBuffers.glMapBufferRangeAddress(GL15.GL_ARRAY_BUFFER, 0, dataSize, MAP_WRITE_INVALIDATE_BUFFER);
                if (dst == 0L) {
                    GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                memCopy(memAddress0(data), dst, dataSize);
                GLSMBuffers.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                return capacity;
            }
            default -> throw new UnsupportedOperationException();
        }
    }
}
