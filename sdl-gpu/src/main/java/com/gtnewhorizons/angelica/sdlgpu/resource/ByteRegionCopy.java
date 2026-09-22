package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class ByteRegionCopy {
    private ByteRegionCopy() {}

    public static void copyByteRegion(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int len) {
        if (src.isDirect() && dst.isDirect()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(src, srcOff), MemoryUtil.memAddress(dst, dstOff), len);
            return;
        }
        if (src.hasArray() && dst.hasArray()) {
            System.arraycopy(src.array(), src.arrayOffset() + srcOff, dst.array(), dst.arrayOffset() + dstOff, len);
            return;
        }
        for (int i = 0; i < len; i++) {
            dst.put(dstOff + i, src.get(srcOff + i));
        }
    }
}
