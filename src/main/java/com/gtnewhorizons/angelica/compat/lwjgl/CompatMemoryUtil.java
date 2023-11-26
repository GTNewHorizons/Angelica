package com.gtnewhorizons.angelica.compat.lwjgl;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class CompatMemoryUtil {
    public static ByteBuffer memReallocDirect(ByteBuffer old, int capacity) {
        ByteBuffer newBuf = BufferUtils.createByteBuffer(capacity);
        int oldPos = old.position();
        old.rewind();
        newBuf.put(old);
        newBuf.position(Math.min(capacity, oldPos));
        return newBuf;
    }

    /**
     * Backported from LWJGL3 under the BSD 3-Clause "New" or "Revised" License license
     *
     * <p>This class provides functionality for managing native memory.
     *
     * <p>All methods in this class will make use of {@link sun.misc.Unsafe} if it's available, for performance. If Unsafe is not available, the fallback
     * implementations make use of reflection and, in the worst-case, JNI.</p>
     *
     * <p>Method names in this class are prefixed with {@code mem} to avoid ambiguities when used with static imports.</p>
     */

    static final sun.misc.Unsafe UNSAFE;

    static {
        UNSAFE = getUnsafeInstance();
    }

    private static sun.misc.Unsafe getUnsafeInstance() {
        java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();

        /*
        Different runtimes use different names for the Unsafe singleton,
        so we cannot use .getDeclaredField and we scan instead. For example:

        Oracle: theUnsafe
        PERC : m_unsafe_instance
        Android: THE_ONE
        */
        for (java.lang.reflect.Field field : fields) {
            if (!field.getType().equals(sun.misc.Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers))) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (sun.misc.Unsafe)field.get(null);
            } catch (Exception ignored) {
            }
            break;
        }

        throw new UnsupportedOperationException("LWJGL requires sun.misc.Unsafe to be available.");
    }


    public static void memPutByte(long ptr, byte value)     { UNSAFE.putByte(null, ptr, value); }
    public static void memPutShort(long ptr, short value)   { UNSAFE.putShort(null, ptr, value); }
    public static void memPutInt(long ptr, int value)       { UNSAFE.putInt(null, ptr, value); }
    public static void memPutLong(long ptr, long value)     { UNSAFE.putLong(null, ptr, value); }
    public static void memPutFloat(long ptr, float value)   { UNSAFE.putFloat(null, ptr, value); }
    public static void memPutDouble(long ptr, double value) { UNSAFE.putDouble(null, ptr, value); }

    public static boolean memGetBoolean(long ptr) { return UNSAFE.getByte(null, ptr) != 0; }
    public static byte memGetByte(long ptr)       { return UNSAFE.getByte(null, ptr); }
    public static short memGetShort(long ptr)     { return UNSAFE.getShort(null, ptr); }
    public static int memGetInt(long ptr)         { return UNSAFE.getInt(null, ptr); }
    public static long memGetLong(long ptr)       { return UNSAFE.getLong(null, ptr); }
    public static float memGetFloat(long ptr)     { return UNSAFE.getFloat(null, ptr); }
    public static double memGetDouble(long ptr)   { return UNSAFE.getDouble(null, ptr); }

}
