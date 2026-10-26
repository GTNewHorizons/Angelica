package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.INDEXED_BUFFER_SLOTS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.acquireDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.initConfig;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.releaseDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMBuffers {

    private GLSMBuffers() {}

    private static final IntOpenHashSet writeMappedBuffers = new IntOpenHashSet();

    public static void glDeleteBuffers(int buffer) {
        rejectExitCapture();
        if (buffer == 0) return;
        invalidateDeletedBuffer(buffer);
        RENDER_BACKEND.deleteBuffers(buffer);
    }

    public static void glDeleteSync(long sync) {
        rejectExitCapture();
        RENDER_BACKEND.deleteSync(sync);
    }

    public static long glFenceSync(int condition, int flags) { rejectExitCapture(); return RENDER_BACKEND.fenceSync(condition, flags); }
    public static int glClientWaitSync(long sync, int flags, long timeout) { rejectExitCapture(); return RENDER_BACKEND.clientWaitSync(sync, flags, timeout); }
    public static void glWaitSync(long sync, int flags, long timeout) { rejectExitCapture(); RENDER_BACKEND.waitSync(sync, flags, timeout); }
    public static int glGetSynci(long sync, int pname, IntBuffer length) { rejectExitCapture(); return RENDER_BACKEND.getSynci(sync, pname, length); }

    public static void glDeleteBuffers(IntBuffer buffers) {
        rejectExitCapture();
        for (int i = buffers.position(); i < buffers.limit(); i++) {
            invalidateDeletedBuffer(buffers.get(i));
        }
        RENDER_BACKEND.deleteBuffers(buffers);
    }

    public static void glDeleteBuffers(int[] buffers) {
        for (int buffer : buffers) {
            glDeleteBuffers(buffer);
        }
    }

    private static void invalidateDeletedBuffer(int buffer) {
        final GLContextState glCtx = ctx();
        if (buffer == 0) return;
        final boolean locked = acquireDrawLock();
        try {
            FfpExtendedAttribs.onDeleteBuffer(glCtx, buffer);
            if (glCtx.boundVBO == buffer) glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (glCtx.vaos.boundEBO == buffer) glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            if (glCtx.boundPixelUnpackBuffer == buffer) glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (glCtx.boundPixelPackBuffer == buffer) glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            if (glCtx.boundCopyReadBuffer == buffer) glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
            if (glCtx.boundCopyWriteBuffer == buffer) glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
            for (Int2IntMap.Entry e : glCtx.boundOtherBuffers.int2IntEntrySet()) {
                if (e.getIntValue() != buffer) continue;
                e.setValue(0);
                RENDER_BACKEND.bindBuffer(e.getIntKey(), 0);
            }
            for (int i = 0; i < INDEXED_BUFFER_SLOTS; i++) {
                if (glCtx.uniformBufferBindings[i] == buffer) glCtx.uniformBufferBindings[i] = 0;
                if (glCtx.storageBufferBindings[i] == buffer) glCtx.storageBufferBindings[i] = 0;
            }
            writeMappedBuffers.remove(buffer);

            glCtx.vaos.onDeleteBuffer(buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    /** glBindBufferBase/Range also set the generic binding point, so they share this. */
    private static void trackBufferBinding(GLContextState glCtx, int target, int buffer) {
        if (target == GL15.GL_ARRAY_BUFFER) {
            // if (boundVBO == buffer) return; TODO figure out why this breaks switching async occlusion mode
            glCtx.boundVBO = buffer;
        } else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {
            glCtx.vaos.onBindEBO(buffer);
        } else if (target == GL21.GL_PIXEL_UNPACK_BUFFER) {
            glCtx.boundPixelUnpackBuffer = buffer;
        } else if (target == GL21.GL_PIXEL_PACK_BUFFER) {
            glCtx.boundPixelPackBuffer = buffer;
        } else if (target == GL31.GL_COPY_READ_BUFFER) {
            glCtx.boundCopyReadBuffer = buffer;
        } else if (target == GL31.GL_COPY_WRITE_BUFFER) {
            glCtx.boundCopyWriteBuffer = buffer;
        } else {
            glCtx.boundOtherBuffers.put(target, buffer);
        }
    }

    public static void glBindBuffer(int target, int buffer) {
        rejectExitCapture();
        final boolean locked = acquireDrawLock();
        try {
            trackBufferBinding(ctx(), target, buffer);
            RENDER_BACKEND.bindBuffer(target, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    private static boolean warnedUntrackedPixelBuffer;

    private static void warnUntrackedPixelBuffer(int binding, String name) {
        if (warnedUntrackedPixelBuffer || initConfig == null || !initConfig.isLwjglDebug()) return;
        final int actual = RENDER_BACKEND.getInteger(binding);
        if (actual == 0) return;
        warnedUntrackedPixelBuffer = true;
        LOGGER.warn("{} is bound to {} but GLSM's cache says 0; a call site bypassed GLSMBuffers.glBindBuffer", name, actual, new Throwable());
    }

    static void suspendPixelUnpackBuffer() {
        if (ctx().boundPixelUnpackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        } else {
            warnUntrackedPixelBuffer(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING, "GL_PIXEL_UNPACK_BUFFER");
        }
    }

    static void restorePixelUnpackBuffer() {
        final GLContextState glCtx = ctx();
        if (glCtx.boundPixelUnpackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, glCtx.boundPixelUnpackBuffer);
        }
    }

    static void suspendPixelPackBuffer() {
        if (ctx().boundPixelPackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        } else {
            warnUntrackedPixelBuffer(GL21.GL_PIXEL_PACK_BUFFER_BINDING, "GL_PIXEL_PACK_BUFFER");
        }
    }

    static void restorePixelPackBuffer() {
        final GLContextState glCtx = ctx();
        if (glCtx.boundPixelPackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, glCtx.boundPixelPackBuffer);
        }
    }

    public static int glGenBuffers() {
        requireContext("glGenBuffers");
        return RENDER_BACKEND.genBuffers();
    }

    public static void glGenBuffers(IntBuffer buffers) {
        requireContext("glGenBuffers");
        for (int i = buffers.position(); i < buffers.limit(); i++) buffers.put(i, RENDER_BACKEND.genBuffers());
    }

    public static void glGenBuffers(int[] buffers) {
        requireContext("glGenBuffers");
        for (int i = 0; i < buffers.length; i++) buffers[i] = RENDER_BACKEND.genBuffers();
    }

    public static int glCreateBuffers() {
        requireContext("glCreateBuffers");
        return RENDER_BACKEND.createBuffers();
    }

    public static void glCreateBuffers(IntBuffer buffers) {
        requireContext("glCreateBuffers");
        for (int i = buffers.position(); i < buffers.limit(); i++) buffers.put(i, RENDER_BACKEND.createBuffers());
    }

    public static void glCreateBuffers(int[] buffers) {
        requireContext("glCreateBuffers");
        for (int i = 0; i < buffers.length; i++) buffers[i] = RENDER_BACKEND.createBuffers();
    }

    private static final int MAP_PERSISTENT_BIT = 0x40;

    private static int bufferForTarget(GLContextState glCtx, int target) {
        if (target == GL15.GL_ARRAY_BUFFER) return glCtx.boundVBO;
        if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) return glCtx.vaos.boundEBO;
        if (target == GL31.GL_COPY_READ_BUFFER) return glCtx.boundCopyReadBuffer;
        if (target == GL31.GL_COPY_WRITE_BUFFER) return glCtx.boundCopyWriteBuffer;
        if (target == GL21.GL_PIXEL_UNPACK_BUFFER) return glCtx.boundPixelUnpackBuffer;
        if (target == GL21.GL_PIXEL_PACK_BUFFER) return glCtx.boundPixelPackBuffer;
        return glCtx.boundOtherBuffers.get(target);
    }

    private static void onBufferRespec(int target) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final int buffer = bufferForTarget(glCtx, target);
        if (buffer != 0) onNamedBufferRespec(glCtx, buffer);
    }

    private static void onNamedBufferRespec(GLContextState glCtx, int buffer) {
        rejectExitCapture();
        final boolean locked = acquireDrawLock();
        try {
            if (!FfpExtendedAttribs.isEmpty()) FfpExtendedAttribs.onBufferRespecified(glCtx, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    private static void onBufferMap(int target, int access) {
        if (access == GL15.GL_READ_ONLY) return;
        onBufferWriteMap(target);
    }

    private static void onBufferMapRange(int target, int access) {
        if ((access & GL30.GL_MAP_WRITE_BIT) == 0) return;
        if ((access & MAP_PERSISTENT_BIT) != 0) {
            markPersistentlyMapped(target);
            return;
        }
        onBufferWriteMap(target);
    }

    private static void onBufferWriteMap(int target) {
        final GLContextState glCtx = ctx();
        final int buffer = bufferForTarget(glCtx, target);
        if (buffer == 0) return;
        final boolean locked = acquireDrawLock();
        try {
            writeMappedBuffers.add(buffer);
            if (!FfpExtendedAttribs.isEmpty()) FfpExtendedAttribs.onBufferRespecified(glCtx, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    private static void onBufferUnmap(int target) {
        final GLContextState glCtx = ctx();
        final int buffer = bufferForTarget(glCtx, target);
        if (buffer == 0) return;
        final boolean locked = acquireDrawLock();
        try {
            if (writeMappedBuffers.remove(buffer) && !FfpExtendedAttribs.isEmpty()) FfpExtendedAttribs.onBufferRespecified(glCtx, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    private static void onBufferStorage(int target, int flags) {
        rejectExitCapture();
        if ((flags & MAP_PERSISTENT_BIT) != 0) {
            if (!RENDER_BACKEND.isBufferImmutable(bufferForTarget(ctx(), target))) markPersistentlyMapped(target);
            return;
        }
        onBufferRespec(target);
    }

    private static void markPersistentlyMapped(int target) {
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            FfpExtendedAttribs.markPersistent(glCtx, bufferForTarget(glCtx, target));
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glBufferData(int target, long size, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, size, usage); }
    public static void glBufferData(int target, ByteBuffer data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, ShortBuffer data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, IntBuffer data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, FloatBuffer data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, DoubleBuffer data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, LongBuffer data, int usage) { glBufferData(target, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glBufferData(int target, int[] data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, float[] data, int usage) { onBufferRespec(target); RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, short[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferData(int target, long[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferData(int target, double[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }

    public static void nglBufferData(int target, long size, long data, int usage) {
        if (data == 0L) {
            glBufferData(target, size, usage);
            return;
        }
        glBufferData(target, MemoryUtilities.memByteBuffer(data, checkedSize(size)), usage);
    }

    public static void glBufferSubData(int target, long offset, ByteBuffer data) { onBufferRespec(target); RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, ShortBuffer data) { onBufferRespec(target); RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, IntBuffer data) { onBufferRespec(target); RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, FloatBuffer data) { onBufferRespec(target); RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, DoubleBuffer data) { onBufferRespec(target); RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, LongBuffer data) { glBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glBufferSubData(int target, long offset, short[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, int[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, long[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, float[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, double[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }

    public static void nglBufferSubData(int target, long offset, long size, long data) { glBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data, checkedSize(size))); }

    public static ByteBuffer glMapBuffer(int target, int access) {
        rejectExitCapture();
        final ByteBuffer buf = RENDER_BACKEND.mapBuffer(target, access);
        if (buf != null) onBufferMap(target, access);
        return buf;
    }
    public static ByteBuffer glMapBuffer(int target, int access, ByteBuffer old_buffer) { return glMapBuffer(target, access); }
    public static ByteBuffer glMapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        rejectExitCapture();
        final ByteBuffer buf = RENDER_BACKEND.mapBuffer(target, access, length, old_buffer);
        if (buf != null) onBufferMap(target, access);
        return buf;
    }
    public static ByteBuffer glMapBufferRange(int target, long offset, long length, int access) {
        rejectExitCapture();
        final ByteBuffer buf = RENDER_BACKEND.mapBufferRange(target, offset, length, access);
        if (buf != null) onBufferMapRange(target, access);
        return buf;
    }
    public static ByteBuffer glMapBufferRange(int target, long offset, long length, int access, ByteBuffer old_buffer) { return glMapBufferRange(target, offset, length, access); }
    public static long glMapBufferRangeAddress(int target, long offset, long length, int access) {
        rejectExitCapture();
        final long address = RENDER_BACKEND.mapBufferRangeAddress(target, offset, length, access);
        if (address != 0L) onBufferMapRange(target, access);
        return address;
    }
    public static void glFlushMappedBufferRange(int target, long offset, long length) { rejectExitCapture(); RENDER_BACKEND.flushMappedBufferRange(target, offset, length); }
    public static boolean glUnmapBuffer(int target) {
        rejectExitCapture();
        onBufferUnmap(target);
        return RENDER_BACKEND.unmapBuffer(target);
    }

    public static void glGetBufferSubData(int target, long offset, ByteBuffer data) { rejectExitCapture(); RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, ShortBuffer data) { rejectExitCapture(); RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, IntBuffer data) { rejectExitCapture(); RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, FloatBuffer data) { rejectExitCapture(); RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, DoubleBuffer data) { rejectExitCapture(); RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, LongBuffer data) { glGetBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glGetBufferSubData(int target, long offset, short[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 1);
        glGetBufferSubData(target, offset, copy);
        copy.asShortBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, int[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 2);
        glGetBufferSubData(target, offset, copy);
        copy.asIntBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, long[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 3);
        glGetBufferSubData(target, offset, copy);
        copy.asLongBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, float[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 2);
        glGetBufferSubData(target, offset, copy);
        copy.asFloatBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, double[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 3);
        glGetBufferSubData(target, offset, copy);
        copy.asDoubleBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }

    public static void nglGetBufferSubData(int target, long offset, long size, long data) { glGetBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data, checkedSize(size))); }

    public static int glGetBufferParameteri(int target, int pname) { rejectExitCapture(); return RENDER_BACKEND.getBufferParameteri(target, pname); }
    public static int glGetBufferParameter(int target, int pname) { return glGetBufferParameteri(target, pname); }
    public static void glGetBufferParameter(int target, int pname, IntBuffer params) { params.put(params.position(), glGetBufferParameteri(target, pname)); }
    public static void glGetBufferParameteriv(int target, int pname, IntBuffer params) { params.put(params.position(), glGetBufferParameteri(target, pname)); }
    public static void glGetBufferParameteriv(int target, int pname, int[] params) { params[0] = glGetBufferParameteri(target, pname); }

    public static void glBufferStorage(int target, long size, int flags) { onBufferStorage(target, flags); RENDER_BACKEND.bufferStorage(target, size, flags); }
    public static void glBufferStorage(int target, ByteBuffer data, int flags) { onBufferStorage(target, flags); RENDER_BACKEND.bufferStorage(target, data, flags); }
    public static void glBufferStorage(int target, ShortBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, IntBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, FloatBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, DoubleBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, short[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, int[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, float[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, double[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }

    public static void glNamedBufferData(int buffer, long size, int usage) { onNamedBufferRespec(ctx(), buffer); RENDER_BACKEND.namedBufferData(buffer, size, usage); }
    public static void glNamedBufferData(int buffer, ByteBuffer data, int usage) { onNamedBufferRespec(ctx(), buffer); RENDER_BACKEND.namedBufferData(buffer, data, usage); }
    public static void glNamedBufferData(int buffer, FloatBuffer data, int usage) { onNamedBufferRespec(ctx(), buffer); RENDER_BACKEND.namedBufferData(buffer, data, usage); }
    public static void glNamedBufferData(int buffer, ShortBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, IntBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, LongBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, DoubleBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, short[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, int[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, long[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, float[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, double[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }

    public static void glNamedBufferSubData(int buffer, long offset, ByteBuffer data) { onNamedBufferRespec(ctx(), buffer); RENDER_BACKEND.namedBufferSubData(buffer, offset, data); }
    public static void glNamedBufferSubData(int buffer, long offset, ShortBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, IntBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, LongBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, FloatBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, DoubleBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, short[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, int[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, long[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, float[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, double[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }

    public static void glClearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) { onBufferRespec(target); RENDER_BACKEND.clearBufferSubData(target, internalFormat, offset, size, format, type, data); }
    public static void glClearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) { onBufferRespec(target); RENDER_BACKEND.clearBufferData(target, internalformat, format, type, data); }
    public static boolean glIsBuffer(int buffer) { rejectExitCapture(); return RENDER_BACKEND.isBuffer(buffer); }
    public static void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) { onBufferRespec(writeTarget); RENDER_BACKEND.copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size); }

    private static int checkedSize(long size) {
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer range does not fit a ByteBuffer wrapper: " + size);
        }
        return (int) size;
    }

    private static ByteBuffer copyOf(short[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 1);
        bb.asShortBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(int[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 2);
        bb.asIntBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(long[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 3);
        bb.asLongBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(float[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 2);
        bb.asFloatBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(double[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 3);
        bb.asDoubleBuffer().put(data);
        return bb;
    }

    public static void glBindBufferBase(int target, int index, int buffer) {
        rejectExitCapture();
        final boolean locked = acquireDrawLock();
        try {
            final GLContextState glCtx = ctx();
            trackBufferBinding(glCtx, target, buffer);
            trackIndexedBufferBinding(glCtx, target, index, buffer);
            RENDER_BACKEND.bindBufferBase(target, index, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glBindBufferRange(int target, int index, int buffer, long offset, long size) {
        rejectExitCapture();
        final boolean locked = acquireDrawLock();
        try {
            final GLContextState glCtx = ctx();
            trackBufferBinding(glCtx, target, buffer);
            trackIndexedBufferBinding(glCtx, target, index, buffer);
            RENDER_BACKEND.bindBufferRange(target, index, buffer, offset, size);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    private static int[] indexedBufferBindings(GLContextState glCtx, int target, int index) {
        if (index < 0 || index >= INDEXED_BUFFER_SLOTS) return null;
        if (target == GL31.GL_UNIFORM_BUFFER) return glCtx.uniformBufferBindings;
        if (target == GL43.GL_SHADER_STORAGE_BUFFER) return glCtx.storageBufferBindings;
        return null;
    }

    private static void trackIndexedBufferBinding(GLContextState glCtx, int target, int index, int buffer) {
        final int[] bindings = indexedBufferBindings(glCtx, target, index);
        if (bindings != null) bindings[index] = buffer;
    }

    public static int getIndexedBufferBinding(int target, int index) {
        if (isCachingEnabled()) {
            final int[] bindings = indexedBufferBindings(ctx(), target, index);
            if (bindings != null) return bindings[index];
        }
        return RENDER_BACKEND.getIndexedBufferBinding(target, index);
    }
    public static long nglMapBuffer(int target, int access) {
        rejectExitCapture();
        final ByteBuffer buf = RENDER_BACKEND.mapBuffer(target, access);
        if (buf != null) onBufferMap(target, access);
        return buf == null ? 0L : MemoryUtilities.memAddress0(buf);
    }

    public static void glMemoryBarrier(int barriers) { rejectExitCapture(); RENDER_BACKEND.memoryBarrier(barriers); }

    public static int getBoundVBO() {
        return ctx().boundVBO;
    }

    public static int getBoundCopyReadBuffer() {
        return ctx().boundCopyReadBuffer;
    }

    public static int getBoundCopyWriteBuffer() {
        return ctx().boundCopyWriteBuffer;
    }
}
