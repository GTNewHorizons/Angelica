package me.jellysquid.mods.sodium.client.gl.device;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawArrays(IntBuffer first, IntBuffer count);

    void multiDrawArraysIndirect(ByteBuffer buffer, int count, int stride);

    void endTessellating();

    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
