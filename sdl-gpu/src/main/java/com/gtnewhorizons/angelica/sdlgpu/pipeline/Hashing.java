package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class Hashing {
    private Hashing() {}

    public static long fmix64(long h, long k) {
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        return (h ^ k) * 0xC4CEB9FE1A85EC53L;
    }

    public static long packHiLo(int hi, int lo) {
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    public static long hashBytes(ByteBuffer buf, int offset, int length) {
        final long base = MemoryUtil.memAddress(buf) + offset;
        long h = 0x9E3779B97F4A7C15L ^ ((long) length * 0xC2B2AE3D27D4EB4FL);
        int i = 0;
        for (; i + 8 <= length; i += 8) {
            h = fmix64(h, MemoryAccess.getLong(base + i));
        }
        if (i < length) {
            long tail = 0;
            int shift = 0;
            for (; i < length; i++) {
                tail |= (((long) (buf.get(offset + i) & 0xFF)) << shift);
                shift += 8;
            }
            tail ^= tail >>> 33;
            tail *= 0xFF51AFD7ED558CCDL;
            h ^= tail;
        }
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    public static long hashFloats(float[] data) {
        final int len = data.length;
        long h = 0x9E3779B97F4A7C15L ^ ((long) len * 0xC2B2AE3D27D4EB4FL);
        int i = 0;
        for (; i + 1 < len; i += 2) {
            h = fmix64(h, packHiLo(Float.floatToRawIntBits(data[i + 1]), Float.floatToRawIntBits(data[i])));
        }
        if (i < len) {
            long k = Float.floatToRawIntBits(data[i]) & 0xFFFFFFFFL;
            k ^= k >>> 33;
            k *= 0xFF51AFD7ED558CCDL;
            h ^= k;
        }
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
