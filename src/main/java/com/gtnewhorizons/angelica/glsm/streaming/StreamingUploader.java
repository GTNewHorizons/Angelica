package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.AngelicaMod;
import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

public final class StreamingUploader {

    public enum UploadStrategy implements NamedState {
        BUFFER_DATA("sodium.options.upload_method.buffer_data"),
        BUFFER_SUB_DATA("sodium.options.upload_method.buffer_subdata"),
        MAP_BUFFER_RANGE("sodium.options.upload_method.map_buffer");


        private final String key;

        UploadStrategy(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return this.key;
        }
    }

    private static final int MAP_WRITE_INVALIDATE_BUFFER = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT;

    public static int upload(ByteBuffer data, int capacity) {
        return upload(AngelicaMod.options().advanced.streamingUploadStrategy, data, capacity);
    }

    public static int upload(UploadStrategy strategy, ByteBuffer data, int capacity) {
        switch (strategy) {
            case BUFFER_DATA -> {
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                return data.remaining();
            }
            case BUFFER_SUB_DATA -> {
                if (data.remaining() > capacity) {
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
                return capacity;
            }
            case MAP_BUFFER_RANGE -> {
                if (data.remaining() > capacity) {
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                final int dataSize = data.remaining();
                final ByteBuffer mapped = LWJGL.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, dataSize, MAP_WRITE_INVALIDATE_BUFFER);
                memCopy(memAddress0(data), memAddress0(mapped), dataSize);
                GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                return capacity;
            }
            default -> throw new UnsupportedOperationException();
        }
    }
}
