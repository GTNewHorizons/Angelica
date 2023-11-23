package com.gtnewhorizons.angelica.compat.lwjgl;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class CompatMemoryUtil {
    public static ByteBuffer memReallocDirect(ByteBuffer old, int capacity) {
        ByteBuffer newBuf = BufferUtils.createByteBuffer(capacity);
        old.rewind();
        newBuf.put(old);
        newBuf.position(0);
        return newBuf;
    }
}
