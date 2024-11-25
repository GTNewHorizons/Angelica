package com.gtnewhorizons.angelica.compat.lwjgl;

import static com.gtnewhorizons.angelica.compat.lwjgl.Pointer.BITS64;
import static org.lwjgl.MemoryUtil.getAddress;

import it.unimi.dsi.fastutil.longs.LongPredicate;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

public class CompatMemoryUtil {
    public static final long NULL = 0;
    private static final Class<? extends ByteBuffer> BUFFER_BYTE;
    private static final Class<? extends IntBuffer> BUFFER_INT;

    private static final long MARK;
    private static final long POSITION;
    private static final long LIMIT;
    private static final long CAPACITY;

    private static final long ADDRESS;

    static {
        ByteBuffer bb = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        BUFFER_BYTE = bb.getClass();
        BUFFER_INT = bb.asIntBuffer().getClass();

        MARK = getMarkOffset();
        POSITION = getPositionOffset();
        LIMIT = getLimitOffset();
        CAPACITY = getCapacityOffset();

        ADDRESS = getAddressOffset();
    }

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
        TODO: verify this
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

    /**
     * Recursively searches an object and its superclasses for a field with the same name and type. If the field isn't
     * public, it may not be stable across library/JVM versions, and these calls are likely not portable. You have been
     * warned!
     */
    private static long getFieldOffset(Class<?> containerType, Class<?> fieldType, String name) {
        Class<?> c = containerType;
        while (c != Object.class) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                if (!field.getType().isAssignableFrom(fieldType) || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                long offset = UNSAFE.objectFieldOffset(field);
                if (field.getName().equals(name)) {
                    return offset;
                }
            }
            c = c.getSuperclass();
        }
        throw new UnsupportedOperationException("Failed to find field offset in class.");
    }

    private static long getFieldOffset(Class<?> containerType, Class<?> fieldType, LongPredicate predicate) {
        Class<?> c = containerType;
        while (c != Object.class) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                if (!field.getType().isAssignableFrom(fieldType) || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                long offset = UNSAFE.objectFieldOffset(field);
                if (predicate.test(offset)) {
                    return offset;
                }
            }
            c = c.getSuperclass();
        }
        throw new UnsupportedOperationException("Failed to find field offset in class.");
    }

    private static long getFieldOffsetInt(Object container, int value) {
        return getFieldOffset(container.getClass(), int.class, offset -> UNSAFE.getInt(container, offset) == value);
    }

    private static long getAddressOffset() {
        final ByteBuffer bb = ByteBuffer.allocateDirect(0);
        return getFieldOffset(bb.getClass(), long.class, "address");

        // TODO: do this portably
        // long MAGIC_ADDRESS = 0xDEADBEEF8BADF00DL & (BITS32 ? 0xFFFF_FFFFL : 0xFFFF_FFFF_FFFF_FFFFL);
        // ByteBuffer bb = Objects.requireNonNull(NewDirectByteBuffer(MAGIC_ADDRESS, 0));
        //return getFieldOffset(bb.getClass(), long.class, offset -> UNSAFE.getLong(bb, offset) == MAGIC_ADDRESS);
    }

    private static final int MAGIC_CAPACITY = 0x0D15EA5E;
    private static final int MAGIC_POSITION = 0x00FACADE;

    private static long getMarkOffset() {
        // TODO: is this actually reliable without NewDirectByteBuffer?
        ByteBuffer bb = ByteBuffer.allocateDirect(0);
        return getFieldOffsetInt(bb, -1);
        // ByteBuffer bb = Objects.requireNonNull(NewDirectByteBuffer(1L, 0));
        // return getFieldOffsetInt(bb, -1);
    }

    private static long getPositionOffset() {
        ByteBuffer bb = ByteBuffer.allocateDirect(MAGIC_CAPACITY);
        bb.position(MAGIC_POSITION);
        return getFieldOffsetInt(bb, MAGIC_POSITION);
    }

    private static long getLimitOffset() {
        ByteBuffer bb = ByteBuffer.allocateDirect(MAGIC_CAPACITY);
        bb.limit(MAGIC_POSITION);
        return getFieldOffsetInt(bb, MAGIC_POSITION);
    }

    private static long getCapacityOffset() {
        ByteBuffer bb = ByteBuffer.allocateDirect(MAGIC_CAPACITY);
        bb.limit(0);
        return getFieldOffsetInt(bb, MAGIC_CAPACITY);
    }

    static IntBuffer wrapBufferInt(long address, int capacity) {
        IntBuffer buffer;
        try {
            buffer = (IntBuffer)UNSAFE.allocateInstance(BUFFER_INT);
        } catch (InstantiationException e) {
            throw new UnsupportedOperationException(e);
        }

        UNSAFE.putLong(buffer, ADDRESS, address);
        UNSAFE.putInt(buffer, MARK, -1);
        UNSAFE.putInt(buffer, LIMIT, capacity);
        UNSAFE.putInt(buffer, CAPACITY, capacity);

        return buffer;
    }
}
