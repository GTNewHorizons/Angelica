/*
 * This class is backported from LWJGL3 under the BSD 3-clause "New" or "Revised" License
 */

package com.gtnewhorizons.angelica.compat.lwjgl;


import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memPutDouble;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memPutFloat;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memPutInt;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memPutLong;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memPutShort;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.memSet;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.wrapBufferFloat;
import static com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil.wrapBufferInt;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.MemoryUtil;

/**
 * An off-heap memory stack.
 *
 * <p>This class should be used in a thread-local manner for stack allocations.</p>
 *
 * @ see Configuration#STACK_SIZE
 * @ see Configuration#DEBUG_STACK
 */
@SuppressWarnings("LombokGetterMayBeUsed")
public class MemoryStack extends Pointer.Default implements AutoCloseable {

    private static final int DEFAULT_STACK_SIZE   = /*Configuration.STACK_SIZE.get(64)*/ 64 * 1024;
    private static final int DEFAULT_STACK_FRAMES = 8;

    private static final ThreadLocal<MemoryStack> TLS = ThreadLocal.withInitial(MemoryStack::create);

    static {
        if (DEFAULT_STACK_SIZE < 0) {
            throw new IllegalStateException("Invalid stack size.");
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final @Nullable ByteBuffer container;

    private final int size;

    private int pointer;

    private   int[] frames;
    protected int   frameIndex;

    /**
     * Creates a new {@code MemoryStack} backed by the specified memory region.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param container the backing memory buffer, may be null
     * @param address   the backing memory address
     * @param size      the backing memory size
     */
    protected MemoryStack(@Nullable ByteBuffer container, long address, int size) {
        super(address);
        this.container = container;

        this.size = size;
        this.pointer = size;

        this.frames = new int[DEFAULT_STACK_FRAMES];
    }

    /**
     * Creates a new {@code MemoryStack} with the default size.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     */
    public static MemoryStack create() {
        return create(DEFAULT_STACK_SIZE);
    }

    /**
     * Creates a new {@code MemoryStack} with the specified size.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param capacity the maximum number of bytes that may be allocated on the stack
     */
    public static MemoryStack create(int capacity) {
        return create(BufferUtils.createByteBuffer(capacity));
    }

    /**
     * Creates a new {@code MemoryStack} backed by the specified memory buffer.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param buffer the backing memory buffer
     */
    public static MemoryStack create(ByteBuffer buffer) {
        long address = MemoryUtil.getAddress(buffer);
        int  size    = buffer.remaining();
        return new MemoryStack(buffer, address, size);
    }

    /**
     * Creates a new {@code MemoryStack} backed by the specified memory region.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param address the backing memory address
     * @param size    the backing memory size
     */
    public static MemoryStack ncreate(long address, int size) {
        return new MemoryStack(null, address, size);
    }

    /**
     * Stores the current stack pointer and pushes a new frame to the stack.
     *
     * <p>This method should be called when entering a method, before doing any stack allocations. When exiting a method, call the {@link #pop} method to
     * restore the previous stack frame.</p>
     *
     * <p>Pairs of push/pop calls may be nested. Care must be taken to:</p>
     * <ul>
     * <li>match every push with a pop</li>
     * <li>not call pop before push has been called at least once</li>
     * <li>not nest push calls to more than the maximum supported depth</li>
     * </ul>
     *
     * @return this stack
     */
    public MemoryStack push() {
        if (frameIndex == frames.length) {
            frameOverflow();
        }

        frames[frameIndex++] = pointer;
        return this;
    }

    private void frameOverflow() {
        if (/*DEBUG*/ false) {
            AngelicaTweaker.LOGGER.warn("[WARNING] Out of frame stack space (" + frames.length + ") in thread: " + Thread.currentThread());
        }
        frames = Arrays.copyOf(frames, frames.length * 3 / 2);
    }

    /**
     * Pops the current stack frame and moves the stack pointer to the end of the previous stack frame.
     *
     * @return this stack
     */
    public MemoryStack pop() {
        pointer = frames[--frameIndex];
        return this;
    }

    /**
     * Calls {@link #pop} on this {@code MemoryStack}.
     *
     * <p>This method should not be used directly. It is called automatically when the {@code MemoryStack} is used as a resource in a try-with-resources
     * statement.</p>
     */
    @Override
    public void close() {
        //noinspection resource
        pop();
    }

    /**
     * Returns the address of the backing off-heap memory.
     *
     * <p>The stack grows "downwards", so the bottom of the stack is at {@code address + size}, while the top is at {@code address}.</p>
     */
    public long getAddress() {
        return address;
    }

    /**
     * Returns the size of the backing off-heap memory.
     *
     * <p>This is the maximum number of bytes that may be allocated on the stack.</p>
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the current frame index.
     *
     * <p>This is the current number of nested {@link #push} calls.</p>
     */
    public int getFrameIndex() {
        return frameIndex;
    }

    /** Returns the memory address at the current stack pointer. */
    public long getPointerAddress() {
        return address + (pointer & 0xFFFF_FFFFL);
    }

    /**
     * Returns the current stack pointer.
     *
     * <p>The stack grows "downwards", so when the stack is empty {@code pointer} is equal to {@code size}. On every allocation {@code pointer} is reduced by
     * the allocated size (after alignment) and {@code address + pointer} points to the first byte of the last allocation.</p>
     *
     * <p>Effectively, this methods returns how many more bytes may be allocated on the stack.</p>
     */
    public int getPointer() {
        return pointer;
    }

    /**
     * Sets the current stack pointer.
     *
     * <p>This method directly manipulates the stack pointer. Using it irresponsibly may break the internal state of the stack. It should only be used in rare
     * cases or in auto-generated code.</p>
     */
    public void setPointer(int pointer) {
        if (/*CHECKS*/ false) {
            checkPointer(pointer);
        }

        this.pointer = pointer;
    }

    private void checkPointer(int pointer) {
        if (pointer < 0 || size < pointer) {
            throw new IndexOutOfBoundsException("Invalid stack pointer");
        }
    }

    private static void checkAlignment(int alignment) {
        if (Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("Alignment must be a power-of-two value.");
        }
    }

    /**
     * Calls {@link #nmalloc(int, int)} with {@code alignment} equal to {@link Pointer#POINTER_SIZE POINTER_SIZE}.
     *
     * @param size the allocation size
     *
     * @return the memory address on the stack for the requested allocation
     */
    public long nmalloc(int size) {
        return nmalloc(POINTER_SIZE, size);
    }

    /**
     * Allocates a block of {@code size} bytes of memory on the stack. The content of the newly allocated block of memory is not initialized, remaining with
     * indeterminate values.
     *
     * @param alignment the required alignment
     * @param size      the allocation size
     *
     * @return the memory address on the stack for the requested allocation
     */
    public long nmalloc(int alignment, int size) {
        // Align address to the specified alignment
        long address = (this.address + pointer - size) & ~Integer.toUnsignedLong(alignment - 1);

        pointer = (int)(address - this.address);
        if (/*CHECKS*/ false && pointer < 0) {
            throw new OutOfMemoryError("Out of stack space.");
        }

        return address;
    }

    /**
     * Allocates a block of memory on the stack for an array of {@code num} elements, each of them {@code size} bytes long, and initializes all its bits to
     * zero.
     *
     * @param alignment the required element alignment
     * @param num       num  the number of elements to allocate
     * @param size      the size of each element
     *
     * @return the memory address on the stack for the requested allocation
     */
    public long ncalloc(int alignment, int num, int size) {
        int  bytes   = num * size;
        long address = nmalloc(alignment, bytes);
        memSet(address, 0, bytes);
        return address;
    }

    // -------------------------------------------------

    /** Unsafe version of {@link #shorts(short)}. */
    public long nshort(short value) {
        long a = nmalloc(2, 2);
        memPutShort(a, value);
        return a;
    }

    // -------------------------------------------------

    /** Int version of {@link #malloc(int)}. */
    public IntBuffer mallocInt(int size) { return wrapBufferInt(nmalloc(4, size << 2), size); }

    /** Unsafe version of {@link #ints(int)}. */
    public long nint(int value) {
        long a = nmalloc(4, 4);
        memPutInt(a, value);
        return a;
    }

    // -------------------------------------------------

    /** Unsafe version of {@link #longs(long)}. */
    public long nlong(long value) {
        long a = nmalloc(8, 8);
        memPutLong(a, value);
        return a;
    }

    // -------------------------------------------------

    /** Float version of {@link #malloc(int)}. */
    public FloatBuffer mallocFloat(int size) { return wrapBufferFloat(nmalloc(4, size << 2), size); }

    /** Unsafe version of {@link #floats(float)}. */
    public long nfloat(float value) {
        long a = nmalloc(4, 4);
        memPutFloat(a, value);
        return a;
    }

    // -------------------------------------------------

    /** Unsafe version of {@link #doubles(double)}. */
    public long ndouble(double value) {
        long a = nmalloc(8, 8);
        memPutDouble(a, value);
        return a;
    }

    // -------------------------------------------------
    // -------------------------------------------------
    // -------------------------------------------------


    /** Returns the stack of the current thread. */
    public static MemoryStack stackGet() {
        return TLS.get();
    }

    /**
     * Calls {@link #push} on the stack of the current thread.
     *
     * @return the stack of the current thread.
     */
    public static MemoryStack stackPush() {
        return stackGet().push();
    }

    /**
     * Calls {@link #pop} on the stack of the current thread.
     *
     * @return the stack of the current thread.
     */
    public static MemoryStack stackPop() {
        return stackGet().pop();
    }

    /** Thread-local version of {@link #nmalloc(int)}. */
    public static long nstackMalloc(int size) { return stackGet().nmalloc(size); }
    /** Thread-local version of {@link #nmalloc(int, int)}. */
    public static long nstackMalloc(int alignment, int size) { return stackGet().nmalloc(alignment, size); }
    /** Thread-local version of {@link #ncalloc}. */
    public static long nstackCalloc(int alignment, int num, int size) { return stackGet().ncalloc(alignment, num, size); }

    // -------------------------------------------------

}
