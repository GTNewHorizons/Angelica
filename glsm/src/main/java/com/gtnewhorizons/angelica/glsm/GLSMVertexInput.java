package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.acquireDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.releaseDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnceTrace;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMVertexInput {

    private GLSMVertexInput() {}

    public static int getBoundEBO() {
        return ctx().vaos.boundEBO;
    }

    private static int texCoordConventional(GLContextState glCtx) {
        return VAOManager.CONV_TEX0 + glCtx.clientActiveTextureUnit;
    }

    static void prepareClientArrays(GLContextState glCtx) {
        final VAOManager vaos = glCtx.vaos;
        if (vaos.hasAnyClientSideEnabledAttrib()) {
            vaos.uploadClientArraysToVBO();
        }
    }

    private static boolean isLegalClientArray(int conv, int size, int type, int stride) {
        return isLegalPointer(VAOManager.isLegalConventional(conv, size, type), "client-array", "client array", conv, size, type, stride);
    }

    private static boolean isLegalVertexAttrib(int index, int size, int type, int stride, boolean integer) {
        return isLegalPointer(VAOManager.isLegalGeneric(index, size, type, integer), "vertex-attrib", "vertex attribute", index, size, type, stride);
    }

    private static boolean isLegalPointer(boolean legal, String key, String what, int slot, int size, int type, int stride) {
        if (stride < 0) warnOnceTrace(key + "-stride", "Negative {} stride {}", what, stride);
        if (legal) return true;
        warnOnceTrace(key + "-" + slot, "Ignoring {} {} with illegal size {} / type 0x{}", what, slot, size, Integer.toHexString(type));
        return false;
    }

    private static boolean isValidVertexAttribIndex(int index, String function) {
        if (index >= 0 && index < VAOManager.MAX_ATTRIBS) return true;
        warnOnceTrace(function, "{}({}) is outside the {} supported vertex attributes; ignoring", function, index, VAOManager.MAX_ATTRIBS);
        return false;
    }

    private static void ffpClientArrayPointer(int conv, int size, int type, boolean normalized, int stride, long offset) {
        rejectExitCapture();
        if (!isLegalClientArray(conv, size, type, stride)) return;
        final GLContextState glCtx = ctx();
        if (glCtx.vaos.setConventional(conv, size, type, normalized, stride, offset, glCtx.boundVBO)) {
            RENDER_BACKEND.vertexAttribPointer(VAOManager.conventionalLocation(conv), size, type, normalized, stride, offset);
        }
    }

    private static void ffpClientArrayPointer(int conv, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        rejectExitCapture();
        if (!isLegalClientArray(conv, size, type, stride)) return;
        ctx().vaos.setConventional(conv, size, type, normalized, stride, pointer);
    }

    static final int FIELD_SIZE = 0, FIELD_TYPE = 1 << 8, FIELD_STRIDE = 2 << 8, FIELD_BINDING = 3 << 8, FIELD_POINTER = 4 << 8;

    static int clientArrayField(GLContextState glCtx, int pname) {
        return switch (pname) {
            case GL11.GL_VERTEX_ARRAY_SIZE -> VAOManager.CONV_POSITION | FIELD_SIZE;
            case GL11.GL_VERTEX_ARRAY_TYPE -> VAOManager.CONV_POSITION | FIELD_TYPE;
            case GL11.GL_VERTEX_ARRAY_STRIDE -> VAOManager.CONV_POSITION | FIELD_STRIDE;
            case GL15.GL_VERTEX_ARRAY_BUFFER_BINDING -> VAOManager.CONV_POSITION | FIELD_BINDING;
            case GL11.GL_VERTEX_ARRAY_POINTER -> VAOManager.CONV_POSITION | FIELD_POINTER;
            case GL11.GL_NORMAL_ARRAY_TYPE -> VAOManager.CONV_NORMAL | FIELD_TYPE;
            case GL11.GL_NORMAL_ARRAY_STRIDE -> VAOManager.CONV_NORMAL | FIELD_STRIDE;
            case GL15.GL_NORMAL_ARRAY_BUFFER_BINDING -> VAOManager.CONV_NORMAL | FIELD_BINDING;
            case GL11.GL_NORMAL_ARRAY_POINTER -> VAOManager.CONV_NORMAL | FIELD_POINTER;
            case GL11.GL_COLOR_ARRAY_SIZE -> VAOManager.CONV_COLOR | FIELD_SIZE;
            case GL11.GL_COLOR_ARRAY_TYPE -> VAOManager.CONV_COLOR | FIELD_TYPE;
            case GL11.GL_COLOR_ARRAY_STRIDE -> VAOManager.CONV_COLOR | FIELD_STRIDE;
            case GL15.GL_COLOR_ARRAY_BUFFER_BINDING -> VAOManager.CONV_COLOR | FIELD_BINDING;
            case GL11.GL_COLOR_ARRAY_POINTER -> VAOManager.CONV_COLOR | FIELD_POINTER;
            case GL11.GL_INDEX_ARRAY_TYPE -> VAOManager.CONV_INDEX | FIELD_TYPE;
            case GL11.GL_INDEX_ARRAY_STRIDE -> VAOManager.CONV_INDEX | FIELD_STRIDE;
            case GL15.GL_INDEX_ARRAY_BUFFER_BINDING -> VAOManager.CONV_INDEX | FIELD_BINDING;
            case GL11.GL_INDEX_ARRAY_POINTER -> VAOManager.CONV_INDEX | FIELD_POINTER;
            case GL11.GL_EDGE_FLAG_ARRAY_STRIDE -> VAOManager.CONV_EDGE_FLAG | FIELD_STRIDE;
            case GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING -> VAOManager.CONV_EDGE_FLAG | FIELD_BINDING;
            case GL11.GL_EDGE_FLAG_ARRAY_POINTER -> VAOManager.CONV_EDGE_FLAG | FIELD_POINTER;
            case GL14.GL_SECONDARY_COLOR_ARRAY_SIZE -> VAOManager.CONV_SECONDARY_COLOR | FIELD_SIZE;
            case GL14.GL_SECONDARY_COLOR_ARRAY_TYPE -> VAOManager.CONV_SECONDARY_COLOR | FIELD_TYPE;
            case GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE -> VAOManager.CONV_SECONDARY_COLOR | FIELD_STRIDE;
            case GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING -> VAOManager.CONV_SECONDARY_COLOR | FIELD_BINDING;
            case GL14.GL_SECONDARY_COLOR_ARRAY_POINTER -> VAOManager.CONV_SECONDARY_COLOR | FIELD_POINTER;
            case GL14.GL_FOG_COORDINATE_ARRAY_TYPE -> VAOManager.CONV_FOG_COORD | FIELD_TYPE;
            case GL14.GL_FOG_COORDINATE_ARRAY_STRIDE -> VAOManager.CONV_FOG_COORD | FIELD_STRIDE;
            case GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING -> VAOManager.CONV_FOG_COORD | FIELD_BINDING;
            case GL14.GL_FOG_COORDINATE_ARRAY_POINTER -> VAOManager.CONV_FOG_COORD | FIELD_POINTER;
            case GL11.GL_TEXTURE_COORD_ARRAY_SIZE -> texCoordConventional(glCtx) | FIELD_SIZE;
            case GL11.GL_TEXTURE_COORD_ARRAY_TYPE -> texCoordConventional(glCtx) | FIELD_TYPE;
            case GL11.GL_TEXTURE_COORD_ARRAY_STRIDE -> texCoordConventional(glCtx) | FIELD_STRIDE;
            case GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING -> texCoordConventional(glCtx) | FIELD_BINDING;
            case GL11.GL_TEXTURE_COORD_ARRAY_POINTER -> texCoordConventional(glCtx) | FIELD_POINTER;
            default -> -1;
        };
    }

    static int clientArrayParam(GLContextState glCtx, int key) {
        final int conv = key & 0xFF;
        final VAOManager.Attrib a = glCtx.vaos.conventional(conv);
        final boolean specified = a != null && a.size != 0;
        return switch (key & ~0xFF) {
            case FIELD_SIZE -> specified ? a.size : conv == VAOManager.CONV_SECONDARY_COLOR ? 3 : 4;
            case FIELD_TYPE -> specified ? a.type : GL11.GL_FLOAT;
            case FIELD_STRIDE -> specified ? a.stride : 0;
            default -> specified ? a.vboId : 0;
        };
    }

    public static ByteBuffer glGetPointer(int pname, long result_size) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final int key = clientArrayField(glCtx, pname);
        if ((key & ~0xFF) != FIELD_POINTER) return null;
        final VAOManager.Attrib a = glCtx.vaos.conventional(key & 0xFF);
        if (a == null) return null;
        final long address = a.clientPointer != null ? MemoryUtilities.memAddress(a.clientPointer) : a.offset;
        return address == 0 ? null : MemoryUtilities.memByteBuffer(address, (int) result_size);
    }

    public static void glEdgeFlagPointer(int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_EDGE_FLAG, 1, GL11.GL_UNSIGNED_BYTE, false, stride, pointer);
    }

    public static void glEdgeFlagPointer(int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_EDGE_FLAG, 1, GL11.GL_UNSIGNED_BYTE, false, stride, pointer_buffer_offset);
    }

    public static void glFogCoordPointer(int stride, FloatBuffer data) {
        ffpClientArrayPointer(VAOManager.CONV_FOG_COORD, 1, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(data));
    }

    public static void glFogCoordPointer(int stride, DoubleBuffer data) {
        ffpClientArrayPointer(VAOManager.CONV_FOG_COORD, 1, GL11.GL_DOUBLE, false, stride, MemoryUtilities.memByteBuffer(data));
    }

    public static void glFogCoordPointer(int type, int stride, long data_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_FOG_COORD, 1, type, false, stride, data_buffer_offset);
    }

    public static void glSecondaryColorPointer(int size, int stride, FloatBuffer data) {
        ffpClientArrayPointer(VAOManager.CONV_SECONDARY_COLOR, size, GL11.GL_FLOAT, true, stride, MemoryUtilities.memByteBuffer(data));
    }

    public static void glSecondaryColorPointer(int size, int stride, DoubleBuffer data) {
        ffpClientArrayPointer(VAOManager.CONV_SECONDARY_COLOR, size, GL11.GL_DOUBLE, true, stride, MemoryUtilities.memByteBuffer(data));
    }

    public static void glSecondaryColorPointer(int size, boolean unsigned, int stride, ByteBuffer data) {
        final int type = unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE;
        ffpClientArrayPointer(VAOManager.CONV_SECONDARY_COLOR, size, type, true, stride, data);
    }

    public static void glSecondaryColorPointer(int size, int type, int stride, long data_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_SECONDARY_COLOR, size, type, true, stride, data_buffer_offset);
    }

    static void pointAttrib(GLContextState glCtx, int index) {
        final VAOManager vaos = glCtx.vaos;
        final VAOManager.Attrib a = vaos.attrib(index);
        if (a == null) return;
        RENDER_BACKEND.vertexAttribDivisor(index, a.divisor);
        if (a.size == 0 || a.vboId == 0 || !vaos.isBufferLive(a)) return;
        if (glCtx.boundVBO != a.vboId) GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, a.vboId);
        if (a.integer) {
            RENDER_BACKEND.vertexAttribIPointer(index, a.size, a.type, a.stride, a.offset);
        } else {
            RENDER_BACKEND.vertexAttribPointer(index, a.size, a.type, a.normalized, a.stride, a.offset);
        }
    }

    private static void repointAttrib(GLContextState glCtx, int index) {
        final int savedVBO = glCtx.boundVBO;
        pointAttrib(glCtx, index);
        if (glCtx.boundVBO != savedVBO) GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
    }

    public static void glVertexPointer(int size, int stride, IntBuffer pointer) {
        glVertexPointer(size, GL11.GL_INT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, ShortBuffer pointer) {
        glVertexPointer(size, GL11.GL_SHORT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_POSITION, size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_POSITION, size, GL11.GL_DOUBLE, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_POSITION, size, type, false, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_POSITION, size, type, false, stride, pointer_buffer_offset);
    }

    public static void glColorPointer(int size, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_COLOR, size, GL11.GL_FLOAT, Usage.COLOR.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glColorPointer(int size, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_COLOR, size, GL11.GL_DOUBLE, Usage.COLOR.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_COLOR, size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer pointer) {
        final int type = unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE;
        ffpClientArrayPointer(VAOManager.CONV_COLOR, size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_COLOR, size, type, Usage.COLOR.isNormalized(), stride, pointer_buffer_offset);
    }

    public static void glNormalPointer(int stride, FloatBuffer pointer) {
        glNormalPointer(GL11.GL_FLOAT, stride, pointer);
    }

    public static void glNormalPointer(int stride, ByteBuffer pointer) {
        glNormalPointer(GL11.GL_BYTE, stride, pointer);
    }

    public static void glNormalPointer(int stride, IntBuffer pointer) {
        glNormalPointer(GL11.GL_INT, stride, pointer);
    }

    public static void glNormalPointer(int stride, DoubleBuffer pointer) {
        glNormalPointer(GL11.GL_DOUBLE, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, type, Usage.NORMAL.isNormalized(), stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, GL11.GL_FLOAT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, ShortBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, GL11.GL_SHORT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, IntBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, GL11.GL_INT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, GL11.GL_DOUBLE, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(VAOManager.CONV_NORMAL, 3, type, Usage.NORMAL.isNormalized(), stride, pointer_buffer_offset);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, type, false, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_FLOAT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, IntBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_INT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, ShortBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_SHORT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, DoubleBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_DOUBLE, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, ShortBuffer pointer) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, GL11.GL_SHORT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, IntBuffer pointer) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, GL11.GL_INT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, GL11.GL_DOUBLE, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(texCoordConventional(ctx()), size, type, false, stride, pointer_buffer_offset);
    }

    public static void glEnableClientState(int cap) {
        rejectExitCapture();
        setClientArrayCap(ctx(), cap, true);
    }

    public static void glDisableClientState(int cap) {
        rejectExitCapture();
        setClientArrayCap(ctx(), cap, false);
    }

    static boolean setClientArrayCap(GLContextState glCtx, int cap, boolean enabled) {
        final int conv = clientStateToConventional(glCtx, cap);
        if (conv < 0) return false;
        setClientArrayEnabled(glCtx, conv, enabled);
        return true;
    }

    private static void setClientArrayEnabled(GLContextState glCtx, int conv, boolean enabled) {
        final VAOManager vaos = glCtx.vaos;
        final boolean repoint = vaos.setConventionalEnabled(conv, enabled);
        final int index = VAOManager.conventionalLocation(conv);
        if (index >= 0) {
            if (repoint) repointAttrib(glCtx, index);
            syncAttribEnable(glCtx, index);
        }
        final int flag = conventionalVertexFlag(conv);
        if (flag == 0) return;
        if (enabled) {
            vaos.enableClientVertexFlag(flag);
        } else {
            vaos.disableClientVertexFlag(flag);
        }
    }

    static void syncAttribEnable(GLContextState glCtx, int index) {
        if (glCtx.vaos.isAttribEnabled(index)) {
            RENDER_BACKEND.enableVertexAttribArray(index);
        } else {
            RENDER_BACKEND.disableVertexAttribArray(index);
        }
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        rejectExitCapture();
        if (!isLegalVertexAttrib(index, size, type, stride, false)) return;
        final GLContextState glCtx = ctx();
        if (glCtx.vaos.setAttribute(index, size, type, normalized, stride, offset, glCtx.boundVBO)) {
            RENDER_BACKEND.vertexAttribPointer(index, size, type, normalized, stride, offset);
        }
    }

    public static void glVertexAttribPointer(int index, int size, boolean normalized, int stride, FloatBuffer pointer) {
        glVertexAttribPointer(index, size, GL11.GL_FLOAT, normalized, stride, pointer);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, FloatBuffer pointer) {
        glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, DoubleBuffer pointer) {
        glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, IntBuffer pointer) {
        glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ShortBuffer pointer) {
        glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        rejectExitCapture();
        if (!isLegalVertexAttrib(index, size, type, stride, false)) return;
        ctx().vaos.setAttribute(index, size, type, normalized, stride, pointer);
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long offset) {
        rejectExitCapture();
        if (!isLegalVertexAttrib(index, size, type, stride, true)) return;
        final GLContextState glCtx = ctx();
        if (glCtx.vaos.setIntegerAttribute(index, size, type, stride, offset, glCtx.boundVBO)) {
            RENDER_BACKEND.vertexAttribIPointer(index, size, type, stride, offset);
        }
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        rejectExitCapture();
        if (!isLegalVertexAttrib(index, size, type, stride, true)) return;
        ctx().vaos.setIntegerAttribute(index, size, type, stride, pointer);
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, IntBuffer pointer) {
        glVertexAttribIPointer(index, size, type, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, ShortBuffer pointer) {
        glVertexAttribIPointer(index, size, type, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribDivisor(int index, int divisor) {
        rejectExitCapture();
        if (ctx().vaos.setDivisor(index, divisor)) RENDER_BACKEND.vertexAttribDivisor(index, divisor);
    }

    public static void glVertexAttribDivisorARB(int index, int divisor) { glVertexAttribDivisor(index, divisor); }
    public static void glBindVertexBuffer(int bindingindex, int buffer, long offset, int stride) { rejectExitCapture(); RENDER_BACKEND.bindVertexBuffer(bindingindex, buffer, offset, stride); }
    public static void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) { rejectExitCapture(); RENDER_BACKEND.vertexAttribFormat(attribindex, size, type, normalized, relativeoffset); }
    public static void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) { rejectExitCapture(); RENDER_BACKEND.vertexAttribIFormat(attribindex, size, type, relativeoffset); }
    public static void glVertexAttribBinding(int attribindex, int bindingindex) { rejectExitCapture(); RENDER_BACKEND.vertexAttribBinding(attribindex, bindingindex); }

    public static void glEnableVertexAttribArray(int index) {
        rejectExitCapture();
        if (!isValidVertexAttribIndex(index, "glEnableVertexAttribArray")) return;
        final GLContextState glCtx = ctx();
        if (glCtx.vaos.enableAttribute(index)) repointAttrib(glCtx, index);
        RENDER_BACKEND.enableVertexAttribArray(index);
    }

    public static void glDisableVertexAttribArray(int index) {
        rejectExitCapture();
        if (!isValidVertexAttribIndex(index, "glDisableVertexAttribArray")) return;
        final GLContextState glCtx = ctx();
        if (glCtx.vaos.disableAttribute(index)) repointAttrib(glCtx, index);
        if (!glCtx.vaos.isAttribEnabled(index)) RENDER_BACKEND.disableVertexAttribArray(index);
    }

    static int clientStateToConventional(GLContextState glCtx, int cap) {
        return switch (cap) {
            case GL11.GL_VERTEX_ARRAY -> VAOManager.CONV_POSITION;
            case GL11.GL_NORMAL_ARRAY -> VAOManager.CONV_NORMAL;
            case GL11.GL_COLOR_ARRAY -> VAOManager.CONV_COLOR;
            case GL11.GL_INDEX_ARRAY -> VAOManager.CONV_INDEX;
            case GL11.GL_EDGE_FLAG_ARRAY -> VAOManager.CONV_EDGE_FLAG;
            case GL14.GL_SECONDARY_COLOR_ARRAY -> VAOManager.CONV_SECONDARY_COLOR;
            case GL14.GL_FOG_COORDINATE_ARRAY -> VAOManager.CONV_FOG_COORD;
            case GL11.GL_TEXTURE_COORD_ARRAY -> texCoordConventional(glCtx);
            default -> -1;
        };
    }

    private static int conventionalVertexFlag(int conv) {
        return switch (conv) {
            case VAOManager.CONV_COLOR -> VertexFlags.COLOR_BIT;
            case VAOManager.CONV_NORMAL -> VertexFlags.NORMAL_BIT;
            case VAOManager.CONV_TEX0 -> VertexFlags.TEXTURE_BIT;
            case VAOManager.CONV_TEX0 + 1 -> VertexFlags.BRIGHTNESS_BIT;
            default -> 0;
        };
    }

    public static void glInterleavedArrays(int format, int stride, long pointer) {
        interleavedArrays(format, stride, pointer, null);
    }

    private static void interleavedPointer(int conv, int size, int type, boolean normalized, int stride, long pointer, ByteBuffer base, int offset) {
        if (base == null) {
            ffpClientArrayPointer(conv, size, type, normalized, stride, pointer + offset);
        } else {
            ffpClientArrayPointer(conv, size, type, normalized, stride, MemoryUtilities.memSlice(base, offset, base.remaining() - offset));
        }
    }

    private static void interleavedArrays(int format, int stride, long pointer, ByteBuffer base) {
        rejectExitCapture();
        // Mesa _mesa_get_interleaved_layout decomposition
        final int f = 4; // sizeof(float)
        final int c = f * ((4 + (f - 1)) / f); // 4 ubytes padded to float alignment = 4

        boolean tflag = false, cflag = false, nflag = false;
        int tcomps = 0, ccomps = 0, vcomps = 0;
        int ctype = 0;
        final int toffset = 0;
        int coffset = 0;
        int noffset = 0;
        int voffset = 0;
        final int defstride;

        switch (format) {
            case GL11.GL_V2F:
                vcomps = 2; defstride = 2 * f; break;
            case GL11.GL_V3F:
                vcomps = 3; defstride = 3 * f; break;
            case GL11.GL_C4UB_V2F:
                cflag = true; ccomps = 4; vcomps = 2;
                ctype = GL11.GL_UNSIGNED_BYTE; voffset = c; defstride = c + 2 * f; break;
            case GL11.GL_C4UB_V3F:
                cflag = true; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_UNSIGNED_BYTE; voffset = c; defstride = c + 3 * f; break;
            case GL11.GL_C3F_V3F:
                cflag = true; ccomps = 3; vcomps = 3;
                ctype = GL11.GL_FLOAT; voffset = 3 * f; defstride = 6 * f; break;
            case GL11.GL_N3F_V3F:
                nflag = true; vcomps = 3;
                voffset = 3 * f; defstride = 6 * f; break;
            case GL11.GL_C4F_N3F_V3F:
                cflag = true; nflag = true; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_FLOAT; noffset = 4 * f; voffset = 7 * f; defstride = 10 * f; break;
            case GL11.GL_T2F_V3F:
                tflag = true; tcomps = 2; vcomps = 3;
                voffset = 2 * f; defstride = 5 * f; break;
            case GL11.GL_T4F_V4F:
                tflag = true; tcomps = 4; vcomps = 4;
                voffset = 4 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_C4UB_V3F:
                tflag = true; cflag = true; tcomps = 2; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_UNSIGNED_BYTE; coffset = 2 * f; voffset = c + 2 * f; defstride = c + 5 * f; break;
            case GL11.GL_T2F_C3F_V3F:
                tflag = true; cflag = true; tcomps = 2; ccomps = 3; vcomps = 3;
                ctype = GL11.GL_FLOAT; coffset = 2 * f; voffset = 5 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_N3F_V3F:
                tflag = true; nflag = true; tcomps = 2; vcomps = 3;
                noffset = 2 * f; voffset = 5 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_C4F_N3F_V3F:
                tflag = true; cflag = true; nflag = true; tcomps = 2; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_FLOAT; coffset = 2 * f; noffset = 6 * f; voffset = 9 * f; defstride = 12 * f; break;
            case GL11.GL_T4F_C4F_N3F_V4F:
                tflag = true; cflag = true; nflag = true; tcomps = 4; ccomps = 4; vcomps = 4;
                ctype = GL11.GL_FLOAT; coffset = 4 * f; noffset = 8 * f; voffset = 11 * f; defstride = 15 * f; break;
            default:
                return; // Invalid format
        }

        if (stride == 0) stride = defstride;

        if (tflag) {
            glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            interleavedPointer(texCoordConventional(ctx()), tcomps, GL11.GL_FLOAT, false, stride, pointer, base, toffset);
        } else {
            glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }

        if (cflag) {
            glEnableClientState(GL11.GL_COLOR_ARRAY);
            interleavedPointer(VAOManager.CONV_COLOR, ccomps, ctype, Usage.COLOR.isNormalized(), stride, pointer, base, coffset);
        } else {
            glDisableClientState(GL11.GL_COLOR_ARRAY);
        }

        if (nflag) {
            glEnableClientState(GL11.GL_NORMAL_ARRAY);
            interleavedPointer(VAOManager.CONV_NORMAL, 3, GL11.GL_FLOAT, Usage.NORMAL.isNormalized(), stride, pointer, base, noffset);
        } else {
            glDisableClientState(GL11.GL_NORMAL_ARRAY);
        }

        glEnableClientState(GL11.GL_VERTEX_ARRAY);
        interleavedPointer(VAOManager.CONV_POSITION, vcomps, GL11.GL_FLOAT, false, stride, pointer, base, voffset);
    }

    public static void glInterleavedArrays(int format, int stride, ByteBuffer pointer) {
        interleavedArrays(format, stride, 0L, pointer);
    }

    public static void glInterleavedArrays(int format, int stride, FloatBuffer pointer) {
        interleavedArrays(format, stride, 0L, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glInterleavedArrays(int format, int stride, DoubleBuffer pointer) {
        interleavedArrays(format, stride, 0L, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glInterleavedArrays(int format, int stride, IntBuffer pointer) {
        interleavedArrays(format, stride, 0L, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glInterleavedArrays(int format, int stride, ShortBuffer pointer) {
        interleavedArrays(format, stride, 0L, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glBindVertexArray(int array) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBindVAO(array);
            // Since the vao needs to be bound to do stuff like state setup & data upload, it'll still execute the bind call.
            // This is technically wrong, but I'm not sure if there's a better solution here.
        }
        bindVertexArrayInternal(glCtx, array == 0 ? glCtx.defaultVAO : array);
    }

    static void bindVertexArrayInternal(GLContextState glCtx, int array) {
        final boolean locked = acquireDrawLock();
        try {
            if (glCtx.boundVAO != array) {
                glCtx.boundVAO = array;
                glCtx.vaos.onBindVertexArrayPre(array);
                RENDER_BACKEND.bindVertexArray(array);
            }
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDeleteVertexArrays(int array) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            glCtx.vaos.onDeleteVertexArray(array);
            if (array == glCtx.boundVAO) {
                // Deleting the bound VAO implicitly unbinds it. Rebind the default VAO.
                bindVertexArrayInternal(glCtx, glCtx.defaultVAO);
            }
            RENDER_BACKEND.deleteVertexArrays(array);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static int glGenVertexArrays() {
        requireContext("glGenVertexArrays");
        return RENDER_BACKEND.genVertexArrays();
    }

    public static boolean glIsVertexArray(int array) {
        rejectExitCapture();
        return RENDER_BACKEND.isVertexArray(array);
    }

    public static void glBindVertexArrayAPPLE(int array) {
        glBindVertexArray(array);
    }

    public static int getBoundVAO() {
        return ctx().boundVAO;
    }
}
