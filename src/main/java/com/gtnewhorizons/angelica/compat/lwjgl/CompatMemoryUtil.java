package com.gtnewhorizons.angelica.compat.lwjgl;

import static com.gtnewhorizons.angelica.compat.lwjgl.Pointer.BITS64;
import static org.lwjgl.MemoryUtil.getAddress;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class CompatMemoryUtil {
    public static final long NULL = 0;

    public static ByteBuffer memReallocDirect(ByteBuffer old, int capacity) {
        ByteBuffer newBuf = BufferUtils.createByteBuffer(capacity);
        int oldPos = old.position();
        old.rewind();
        newBuf.put(old);
        newBuf.position(Math.min(capacity, oldPos));
        return newBuf;
    }

    public static IntBuffer memReallocDirect(IntBuffer old, int capacity) {
        IntBuffer newBuf = BufferUtils.createIntBuffer(capacity);
        int oldPos = old.position();
        old.rewind();
        newBuf.put(old);
        newBuf.position(Math.min(capacity, oldPos));
        return newBuf;
    }

    public static FloatBuffer memReallocDirect(FloatBuffer old, int capacity) {
        FloatBuffer newBuf = BufferUtils.createFloatBuffer(capacity);
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

    /**
     * Sets all bytes in a specified block of memory to a fixed value (usually zero).
     *
     * @param ptr   the starting memory address
     * @param value the value to set (memSet will convert it to unsigned byte)
     */
    public static void memSet(ByteBuffer ptr, int value) { memSet(getAddress(ptr), value, ptr.remaining()); }


    private static final int  FILL_PATTERN_32 = Integer.divideUnsigned(-1, 255);
    private static final long FILL_PATTERN_64 = Long.divideUnsigned(-1L, 255L);

    /**
     * Sets all bytes in a specified block of memory to a fixed value (usually zero).
     *
     * @param ptr   the starting memory address
     * @param value the value to set (memSet will convert it to unsigned byte)
     * @param bytes the number of bytes to set
     */
    public static void memSet(long ptr, int value, long bytes) {
        if (/*DEBUG*/ false && (ptr == NULL || bytes < 0)) {
            throw new IllegalArgumentException();
        }

        /*
        - Unsafe.setMemory is very slow.
        - A custom Java loop is fastest at small sizes, approximately up to 256 bytes.
        - The native memset becomes fastest at bigger sizes, when the JNI overhead becomes negligible.
         */

        //UNSAFE.setMemory(ptr, bytes, (byte)(value & 0xFF));
        //if (bytes < 256L) {
            int p = (int)ptr;
            if (BITS64) {
                if ((p & 7) == 0) {
                    memSet64(ptr, value, (int)bytes & 0xFF);
                    return;
                }
            } else {
                if ((p & 3) == 0) {
                    memSet32(p, value, (int)bytes & 0xFF);
                    return;
                }
            }
        //}
        //nmemset(ptr, value, bytes);
    }
    private static void memSet64(long ptr, int value, int bytes) {
        int aligned = bytes & ~7;

        // Aligned body
        long valuel = (value & 0xFF) * FILL_PATTERN_64;
        for (int i = 0; i < aligned; i += 8) {
            UNSAFE.putLong(null, ptr + i, valuel);
        }

        // Unaligned tail
        byte valueb = (byte)(value & 0xFF);
        for (int i = aligned; i < bytes; i++) {
            UNSAFE.putByte(null, ptr + i, valueb);
        }
    }
    private static void memSet32(int ptr, int value, int bytes) {
        int aligned = bytes & ~3;

        // Aligned body
        int vi = (value & 0xFF) * FILL_PATTERN_32;
        for (int i = 0; i < aligned; i += 4) {
            UNSAFE.putInt(null, (ptr + i) & 0xFFFF_FFFFL, vi);
        }

        // Unaligned tail
        byte vb = (byte)(value & 0xFF);
        for (int i = aligned; i < bytes; i++) {
            UNSAFE.putByte(null, (ptr + i) & 0xFFFF_FFFFL, vb);
        }
    }
}
