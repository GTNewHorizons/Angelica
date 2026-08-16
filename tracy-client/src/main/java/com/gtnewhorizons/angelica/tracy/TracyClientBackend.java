package com.gtnewhorizons.angelica.tracy;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.glsm.profiling.ZoneStack;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;
import org.lwjgl.system.libffi.LibFFI;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.nmemAllocChecked;

@Lwjgl3Aware
public final class TracyClientBackend implements TracyBackend {
    private static final Logger LOGGER = LogManager.getLogger("Tracy");
    private static final int MAX_TEXT = 8192;
    private static final int ACTIVE_OFFSET = 4;

    private SharedLibrary lib;
    private TracyLibrary sym;
    private SrcLocInterner interner;
    private long emptyString;
    private int ctxSize;
    int zoneCtxSize() {
        return ctxSize;
    }

    @Override
    public boolean init() {
        try {
            if (Pointer.POINTER_SIZE != 8) {
                LOGGER.warn("Tracy: 64-bit JVM required");
                return false;
            }
            lib = TracyNativeLoader.load();
            if (lib == null) return false;
            sym = TracyLibrary.resolve(lib);
            if (sym == null) return false;

            ctxSize = (int) JNI.invokeJ(sym.zoneCtxSize);
            if (ctxSize <= 0 || Integer.bitCount(ctxSize) != 1) {
                LOGGER.warn("Tracy: zone context size {} is not a power of two; slot addressing would be wrong", ctxSize);
                return false;
            }

            emptyString = nmemAllocChecked(1);
            memPutByte(emptyString, (byte) 0);

            interner = new SrcLocInterner(this::allocSrcLoc, SystemProperties.TRACY_MAX_SRC_LOCS);

            JNI.invokeV(sym.startupProfiler);
            if (JNI.invokeI(sym.profilerStarted) == 0) {
                LOGGER.warn("Tracy: profiler failed to start");
                return false;
            }
            appInfo("Angelica Tracy profiling (MC 1.7.10, Tracy " + TracyNativeLoader.TRACY_VERSION + ")");
            LOGGER.info("Tracy: client started (Tracy {}, lib {})", TracyNativeLoader.TRACY_VERSION, lib.getPath());
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Tracy: init failed: {}", t.toString());
            return false;
        }
    }

    private long allocSrcLoc(String name, int color) {
        final ByteBuffer nameUtf8 = MemoryUtil.memUTF8(name);
        final long srcLoc = nmemAllocChecked(32);
        memPutAddress(srcLoc, memAddress(nameUtf8));
        memPutAddress(srcLoc + 8, emptyString);
        memPutAddress(srcLoc + 16, emptyString);
        memPutInt(srcLoc + 24, 0);
        memPutInt(srcLoc + 28, color);
        return srcLoc;
    }

    @Override
    public long internSrcLoc(String name, int color) {
        return interner.intern(name, color);
    }

    @Override
    public long dynamicSrcLoc() {
        return interner.dynamicSrcLoc();
    }

    private static final ConcurrentLinkedQueue<SlotArena> ARENAS = new ConcurrentLinkedQueue<>();

    private static final class SlotArena {
        private final long base;
        private final int strideShift;
        private int depth;

        SlotArena(int stride) {
            this.strideShift = Integer.numberOfTrailingZeros(stride);
            this.base = nmemAllocChecked((long) stride * ZoneStack.MAX_DEPTH);
        }

        long top() {
            return depth == ZoneStack.MAX_DEPTH ? NULL : base + ((long) depth << strideShift);
        }

        void unwindTo(long slot) {
            final long index = (slot - base) >> strideShift;
            if (index >= 0 && index < ZoneStack.MAX_DEPTH) depth = (int) index;
        }
    }

    private final ThreadLocal<SlotArena> slots = ThreadLocal.withInitial(() -> {
        final SlotArena arena = new SlotArena(ctxSize);
        ARENAS.add(arena);
        return arena;
    });

    @Override
    public long beginZone(long srcLoc) {
        final SlotArena arena = slots.get();
        final long slot = arena.top();
        if (slot == NULL) return 0L;
        JNI.invokePPV(srcLoc, 1, slot, sym.zoneBegin);
        if (memGetInt(slot + ACTIVE_OFFSET) == 0) return 0L;
        arena.depth++;
        return slot;
    }

    @Override
    public void endZone(long ctx) {
        if (ctx == 0L) return;
        slots.get().unwindTo(ctx);
        JNI.invokePV(ctx, sym.zoneEnd);
    }

    @Override
    public void zoneText(long ctx, String text) {
        if (ctx == 0L) return;
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer utf8 = stack.UTF8(truncate(text), false);
            JNI.invokePPPV(ctx, memAddress(utf8), utf8.remaining(), sym.zoneText);
        }
    }

    @Override
    public void zoneValue(long ctx, long value) {
        if (ctx == 0L) return;
        JNI.invokePJV(ctx, value, sym.zoneValue);
    }

    @Override
    public void frameMark() {
        JNI.invokePV(NULL, sym.frameMark);
    }

    @Override
    public long internFrameName(String name) {
        return memAddress(MemoryUtil.memUTF8(name));
    }

    @Override
    public void frameMark(long namePtr) {
        JNI.invokePV(namePtr, sym.frameMark);
    }

    @Override
    public long internPlotName(String name, int format) {
        final long namePtr = memAddress(MemoryUtil.memUTF8(name));
        JNI.invokePV(namePtr, format, 0, 1, 0, sym.plotConfig);
        return namePtr;
    }

    @Override
    public void plotInt(long namePtr, long value) {
        JNI.invokePJV(namePtr, value, sym.plotInt);
    }

    @Override
    public void plot(long namePtr, double value) {
        JNI.invokePJV(namePtr, Double.doubleToRawLongBits(value), sym.plot);
    }

    @Override
    public void message(String text, int severity) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer utf8 = stack.UTF8(truncate(text), false);
            JNI.invokePPV(severity, 0, 0, (long) utf8.remaining(), memAddress(utf8), sym.message);
        }
    }

    @Override
    public long sectionEnter(int category, String text) {
        try (MemoryStack stack = stackPush()) {
            return Integer.toUnsignedLong(JNI.invokePI(category, memAddress(stack.UTF8(truncate(text), true)), sym.sectionEnter));
        }
    }

    @Override
    public void sectionLeave(long id) {
        JNI.invokeV((int) id, sym.sectionLeave);
    }

    @Override
    public void sectionSetup(int category, String name) {
        try (MemoryStack stack = stackPush()) {
            JNI.invokePV(category, memAddress(stack.UTF8(truncate(name), true)), sym.sectionSetup);
        }
    }

    private void appInfo(String text) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer utf8 = stack.UTF8(text, false);
            JNI.invokePPV(memAddress(utf8), utf8.remaining(), sym.messageAppinfo);
        }
    }

    @Override
    public void setCurrentThreadName(String name) {
        try (MemoryStack stack = stackPush()) {
            JNI.invokePV(memAddress(stack.UTF8(name, true)), sym.setThreadName);
        }
    }

    @Override
    public boolean isConnected() {
        return JNI.invokeI(sym.connected) != 0;
    }

    @Override
    public void shutdown() {
        JNI.invokeV(sym.shutdownProfiler);
        for (SlotArena arena = ARENAS.poll(); arena != null; arena = ARENAS.poll()) {
            MemoryUtil.nmemFree(arena.base);
        }
    }

    private static String truncate(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    private static final int GPU_QUERY_COUNT = 16 * 1024;
    private static final byte GPU_CONTEXT_ID = 0;
    private static final byte GPU_CONTEXT_TYPE_OPENGL = 1;

    private RenderBackend renderBackend;
    private int[] gpuQueries;
    private int gpuHead;
    private int gpuTail;
    private int gpuPendingEnds;
    private boolean gpuInitAttempted;
    private boolean gpuReady;
    private int gpuCollectsSinceSync;
    private FFICIF gpuZoneBeginCif;
    private FFICIF gpuZoneEndCif;
    private FFICIF gpuTimeCif;
    private FFICIF gpuTimeSyncCif;
    private FFICIF gpuNewContextCif;
    private FFICIF gpuContextNameCif;

    @Override
    public boolean gpuInit() {
        if (gpuInitAttempted) return gpuReady;
        gpuInitAttempted = true;
        try {
            final RenderBackend rb = BackendManager.RENDER_BACKEND;
            if (!rb.supportsGpuProfiling()) {
                LOGGER.info("Tracy: GPU zones unavailable (render backend has no timer queries)");
                return false;
            }
            prepGpuCifs();
            renderBackend = rb;
            gpuQueries = new int[GPU_QUERY_COUNT];
            emitGpuNewContext(rb.getGpuTimestamp());
            emitGpuContextName("OpenGL");
            gpuReady = true;
            LOGGER.info("Tracy: GPU zones enabled");
        } catch (Throwable t) {
            LOGGER.warn("Tracy: GPU init failed: {}", t.toString());
        }
        return gpuReady;
    }

    void prepGpuCifs() {
        if (gpuZoneBeginCif != null) return;
        gpuZoneBeginCif = prepStructCif(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint16, LibFFI.ffi_type_uint8);
        gpuZoneEndCif = prepStructCif(LibFFI.ffi_type_uint16, LibFFI.ffi_type_uint8);
        gpuTimeCif = prepStructCif(LibFFI.ffi_type_sint64, LibFFI.ffi_type_uint16, LibFFI.ffi_type_uint8);
        gpuTimeSyncCif = prepStructCif(LibFFI.ffi_type_sint64, LibFFI.ffi_type_uint8);
        gpuNewContextCif = prepStructCif(LibFFI.ffi_type_sint64, LibFFI.ffi_type_float, LibFFI.ffi_type_uint8, LibFFI.ffi_type_uint8, LibFFI.ffi_type_uint8);
        gpuContextNameCif = prepStructCif(LibFFI.ffi_type_uint8, LibFFI.ffi_type_pointer, LibFFI.ffi_type_uint16);
    }

    void emitGpuNewContext(long gpuTime) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer data = stack.calloc(16);
            data.putLong(0, gpuTime);
            data.putFloat(8, 1.0f);
            data.put(12, GPU_CONTEXT_ID);
            data.put(13, (byte) 0);
            data.put(14, GPU_CONTEXT_TYPE_OPENGL);
            LibFFI.ffi_call(gpuNewContextCif, sym.gpuNewContext, null, stack.pointers(memAddress(data)));
        }
    }

    void emitGpuContextName(String name) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer utf8 = stack.UTF8(name, false);
            final ByteBuffer data = stack.calloc(24);
            data.put(0, GPU_CONTEXT_ID);
            data.putLong(8, memAddress(utf8));
            data.putShort(16, (short) utf8.remaining());
            LibFFI.ffi_call(gpuContextNameCif, sym.gpuContextName, null, stack.pointers(memAddress(data)));
        }
    }

    void emitGpuZoneBegin(long srcLoc, int queryId) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer data = stack.calloc(16);
            data.putLong(0, srcLoc);
            data.putShort(8, (short) queryId);
            data.put(10, GPU_CONTEXT_ID);
            LibFFI.ffi_call(gpuZoneBeginCif, sym.gpuZoneBegin, null, stack.pointers(memAddress(data)));
        }
    }

    void emitGpuZoneEnd(int queryId) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer data = stack.calloc(4);
            data.putShort(0, (short) queryId);
            data.put(2, GPU_CONTEXT_ID);
            LibFFI.ffi_call(gpuZoneEndCif, sym.gpuZoneEnd, null, stack.pointers(memAddress(data)));
        }
    }

    void emitGpuTime(long gpuTime, int queryId) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer data = stack.calloc(16);
            data.putLong(0, gpuTime);
            data.putShort(8, (short) queryId);
            data.put(10, GPU_CONTEXT_ID);
            LibFFI.ffi_call(gpuTimeCif, sym.gpuTime, null, stack.pointers(memAddress(data)));
        }
    }

    void emitGpuTimeSync(long gpuTime) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer data = stack.calloc(16);
            data.putLong(0, gpuTime);
            data.put(8, GPU_CONTEXT_ID);
            LibFFI.ffi_call(gpuTimeSyncCif, sym.gpuTimeSync, null, stack.pointers(memAddress(data)));
        }
    }

    /** Test seam: exercises every GPU struct-by-value FFI marshalling path without GL. */
    private static FFICIF prepStructCif(FFIType... members) {
        final PointerBuffer elements = MemoryUtil.memAllocPointer(members.length + 1);
        for (int i = 0; i < members.length; i++) elements.put(i, members[i].address());
        elements.put(members.length, NULL);
        final FFIType struct = FFIType.calloc().type((short) LibFFI.FFI_TYPE_STRUCT).elements(elements);
        final PointerBuffer argTypes = MemoryUtil.memAllocPointer(1);
        argTypes.put(0, struct.address());
        final FFICIF cif = FFICIF.malloc();
        final int status = LibFFI.ffi_prep_cif(cif, LibFFI.FFI_DEFAULT_ABI, LibFFI.ffi_type_void, argTypes);
        if (status != LibFFI.FFI_OK) throw new IllegalStateException("ffi_prep_cif (gpu struct) failed: " + status);
        return cif;
    }

    private int nextGpuQuery() {
        final int id = gpuHead;
        gpuHead = (gpuHead + 1) % GPU_QUERY_COUNT;
        int query = gpuQueries[id];
        if (query == 0) {
            query = renderBackend.genQuery();
            gpuQueries[id] = query;
        }
        renderBackend.queryCounter(query);
        return id;
    }

    @Override
    public boolean gpuBeginZone(long srcLoc) {
        if (!gpuReady || !isConnected()) return false;
        final int inFlight = (gpuHead - gpuTail + GPU_QUERY_COUNT) % GPU_QUERY_COUNT;
        if (GPU_QUERY_COUNT - 1 - inFlight < gpuPendingEnds + 2) return false;
        final int queryId = nextGpuQuery();
        emitGpuZoneBegin(srcLoc, queryId);
        gpuPendingEnds++;
        return true;
    }

    @Override
    public void gpuEndZone() {
        if (!gpuReady || gpuPendingEnds == 0) return;
        gpuPendingEnds--;
        emitGpuZoneEnd(nextGpuQuery());
    }

    @Override
    public void gpuCollect() {
        if (!gpuReady) return;
        if (!isConnected()) {
            gpuHead = 0;
            gpuTail = 0;
            return;
        }
        if (++gpuCollectsSinceSync >= 100) {
            gpuCollectsSinceSync = 0;
            emitGpuTimeSync(renderBackend.getGpuTimestamp());
        }
        while (gpuTail != gpuHead) {
            final int query = gpuQueries[gpuTail];
            if (!renderBackend.isQueryResultAvailable(query)) return;
            emitGpuTime(renderBackend.getQueryResult64(query), gpuTail);
            gpuTail = (gpuTail + 1) % GPU_QUERY_COUNT;
        }
    }
}
