package com.gtnewhorizons.angelica.sdlgpu.util;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

@SuppressWarnings("all")
public final class MemoryAccess {

    private static final Logger LOG = LogManager.getLogger("Angelica-MemoryAccess");
    private static final boolean USE_UNSAFE;
    private static final sun.misc.Unsafe UNSAFE;
    private static final long FLOAT_ARRAY_BASE_OFFSET;

    static {
        sun.misc.Unsafe u = null;
        try {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable ignored) {}
        UNSAFE = u;
        FLOAT_ARRAY_BASE_OFFSET = (u != null) ? u.arrayBaseOffset(float[].class) : 0L;

        boolean canUse;
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 26) {
            canUse = u != null;
        } else {
            canUse = u != null && probeUnsafeMemoryAccess(u);
        }
        USE_UNSAFE = canUse;
        LOG.info("MemoryAccess: Java {}, using {} path", javaVersion, USE_UNSAFE ? "Unsafe (GTNHLib)" : "VarHandle (LWJGL3 fallback)");
    }

    private static boolean probeUnsafeMemoryAccess(sun.misc.Unsafe unsafe) {
        try {
            long probe = MemoryUtil.nmemAlloc(8);
            try {
                unsafe.putLong(probe, 0L);
                unsafe.getLong(probe);
                return true;
            } finally {
                MemoryUtil.nmemFree(probe);
            }
        } catch (Throwable t) {
            LOG.warn("Unsafe memory access not available, falling back to VarHandle: {}", t.getMessage());
            return false;
        }
    }

    public static boolean isUsingUnsafe() { return USE_UNSAFE; }

    public static byte getByte(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getByte(p) : MemoryUtil.memGetByte(p);
    }

    public static short getShort(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getShort(p) : MemoryUtil.memGetShort(p);
    }

    public static int getInt(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getInt(p) : MemoryUtil.memGetInt(p);
    }

    public static float getFloat(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getFloat(p) : MemoryUtil.memGetFloat(p);
    }

    public static long getLong(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getLong(p) : MemoryUtil.memGetLong(p);
    }

    public static long getAddress(long p) {
        return USE_UNSAFE ? GTNHUnsafe.getAddress(p) : MemoryUtil.memGetAddress(p);
    }

    public static void putByte(long p, byte v) {
        if (USE_UNSAFE) GTNHUnsafe.putByte(p, v); else MemoryUtil.memPutByte(p, v);
    }

    public static void putShort(long p, short v) {
        if (USE_UNSAFE) GTNHUnsafe.putShort(p, v); else MemoryUtil.memPutShort(p, v);
    }

    public static void putInt(long p, int v) {
        if (USE_UNSAFE) GTNHUnsafe.putInt(p, v); else MemoryUtil.memPutInt(p, v);
    }

    public static void putFloat(long p, float v) {
        if (USE_UNSAFE) GTNHUnsafe.putFloat(p, v); else MemoryUtil.memPutFloat(p, v);
    }

    public static void putLong(long p, long v) {
        if (USE_UNSAFE) GTNHUnsafe.putLong(p, v); else MemoryUtil.memPutLong(p, v);
    }

    public static void putAddress(long p, long v) {
        if (USE_UNSAFE) GTNHUnsafe.putAddress(p, v); else MemoryUtil.memPutAddress(p, v);
    }

    public static void copyFloatsToAddr(float[] src, int srcOff, long dstAddr, int floats) {
        if (floats <= 0) return;
        if (USE_UNSAFE) {
            UNSAFE.copyMemory(src, FLOAT_ARRAY_BASE_OFFSET + ((long) srcOff) * 4L,
                              null, dstAddr, ((long) floats) * 4L);
        } else {
            for (int i = 0; i < floats; i++) {
                MemoryUtil.memPutFloat(dstAddr + ((long) i) * 4L, src[srcOff + i]);
            }
        }
    }

    private static final class GTNHUnsafe {
        public static byte getByte(long p) { return MemoryUtilities.memGetByte(p); }
        public static short getShort(long p) { return MemoryUtilities.memGetShort(p); }
        public static int getInt(long p) { return MemoryUtilities.memGetInt(p); }
        public static float getFloat(long p) { return MemoryUtilities.memGetFloat(p); }
        public static long getLong(long p) { return MemoryUtilities.memGetLong(p); }
        public static long getAddress(long p) { return MemoryUtilities.memGetAddress(p); }
        public static void putByte(long p, byte v) { MemoryUtilities.memPutByte(p, v); }
        public static void putShort(long p, short v) { MemoryUtilities.memPutShort(p, v); }
        public static void putInt(long p, int v) { MemoryUtilities.memPutInt(p, v); }
        public static void putFloat(long p, float v) { MemoryUtilities.memPutFloat(p, v); }
        public static void putLong(long p, long v) { MemoryUtilities.memPutLong(p, v); }
        public static void putAddress(long p, long v) { MemoryUtilities.memPutAddress(p, v); }
    }
}
