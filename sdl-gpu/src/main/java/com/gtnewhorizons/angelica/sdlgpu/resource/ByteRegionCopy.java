package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class ByteRegionCopy {
    private ByteRegionCopy() {}

    public static void copyByteRegion(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int len) {
        if ((srcOff | dstOff | len) < 0 || srcOff + (long) len > src.capacity() || dstOff + (long) len > dst.capacity()) {
            throw new IllegalStateException("copyByteRegion out of bounds: len=" + len + " srcOff=" + srcOff + " src.capacity=" + src.capacity() + " dstOff=" + dstOff + " dst.capacity=" + dst.capacity());
        }
        if (src.isDirect() && dst.isDirect()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(src, srcOff), MemoryUtil.memAddress(dst, dstOff), len);
            return;
        }
        dst.put(dstOff, src, srcOff, len);
    }
}
