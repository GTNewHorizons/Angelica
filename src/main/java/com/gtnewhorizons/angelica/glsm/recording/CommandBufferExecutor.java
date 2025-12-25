package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;
import static com.gtnewhorizons.angelica.glsm.recording.CommandBuffer.*;

/**
 * Executes commands from a CommandBuffer
 */
public final class CommandBufferExecutor {
    private CommandBufferExecutor() {}

    // Static reusable buffer for matrix ops (render thread only, LWJGL2/3 compatible)
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    // Static reusable buffer for fog/light/material params
    private static final FloatBuffer PARAMS_BUFFER = BufferUtils.createFloatBuffer(4);
    // Static reusable buffer for draw buffers
    private static final int MAX_DRAW_BUFFERS = 8;
    private static final IntBuffer DRAW_BUFFERS_BUFFER = BufferUtils.createIntBuffer(MAX_DRAW_BUFFERS);

    /**
     * Execute all commands in the buffer.
     *
     * @param buffer The command buffer (position should be at 0, limit at size)
     * @param complexObjects Array of complex objects (TexImage2DCmd, etc.)
     * @param ownedVbos Array of VBOs owned by the display list (indexed by DrawRange commands)
     */
    public static void execute(ByteBuffer buffer, Object[] complexObjects, VertexBuffer[] ownedVbos) {
        long ptr = memAddress(buffer);
        final long end = ptr + buffer.limit();

        while (ptr < end) {
            final int cmd = memGetInt(ptr);
            ptr += 4;

            switch (cmd) {
                // === Single int commands ===
                case GLCommand.ENABLE -> {
                    GLStateManager.glEnable(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.DISABLE -> {
                    GLStateManager.glDisable(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.CLEAR -> {
                    GLStateManager.glClear(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.CLEAR_STENCIL -> {
                    GLStateManager.glClearStencil(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.CULL_FACE -> {
                    GLStateManager.glCullFace(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.DEPTH_FUNC -> {
                    GLStateManager.glDepthFunc(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.SHADE_MODEL -> {
                    GLStateManager.glShadeModel(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.LOGIC_OP -> {
                    GLStateManager.glLogicOp(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.MATRIX_MODE -> {
                    GLStateManager.glMatrixMode(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.ACTIVE_TEXTURE -> {
                    GLStateManager.glActiveTexture(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.USE_PROGRAM -> {
                    GLStateManager.glUseProgram(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.PUSH_ATTRIB -> {
                    GLStateManager.glPushAttrib(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.POP_ATTRIB -> {
                    GLStateManager.glPopAttrib();
                    ptr += 4; // Skip unused padding
                }
                case GLCommand.LOAD_IDENTITY -> {
                    GLStateManager.glLoadIdentity();
                }
                case GLCommand.PUSH_MATRIX -> {
                    GLStateManager.glPushMatrix();
                }
                case GLCommand.POP_MATRIX -> {
                    GLStateManager.glPopMatrix();
                }
                case GLCommand.STENCIL_MASK -> {
                    GLStateManager.glStencilMask(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.DEPTH_MASK -> {
                    GLStateManager.glDepthMask(memGetInt(ptr) != 0);
                    ptr += 4;
                }
                case GLCommand.FRONT_FACE -> {
                    GLStateManager.glFrontFace(memGetInt(ptr));
                    ptr += 4;
                }

                // === Two int commands ===
                case GLCommand.BIND_TEXTURE -> {
                    final int target = memGetInt(ptr);
                    final int texture = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glBindTexture(target, texture);
                }
                case GLCommand.POLYGON_MODE -> {
                    final int face = memGetInt(ptr);
                    final int mode = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glPolygonMode(face, mode);
                }
                case GLCommand.COLOR_MATERIAL -> {
                    final int face = memGetInt(ptr);
                    final int mode = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glColorMaterial(face, mode);
                }
                case GLCommand.LINE_STIPPLE -> {
                    final int factor = memGetInt(ptr);
                    final int pattern = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glLineStipple(factor, (short) pattern);
                }
                case GLCommand.STENCIL_MASK_SEPARATE -> {
                    final int face = memGetInt(ptr);
                    final int mask = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glStencilMaskSeparate(face, mask);
                }
                case GLCommand.FOGI -> {
                    final int pname = memGetInt(ptr);
                    final int param = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glFogi(pname, param);
                }
                case GLCommand.HINT -> {
                    final int target = memGetInt(ptr);
                    final int mode = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glHint(target, mode);
                }

                // === Three int commands ===
                case GLCommand.STENCIL_FUNC -> {
                    final int func = memGetInt(ptr);
                    final int ref = memGetInt(ptr + 4);
                    final int mask = memGetInt(ptr + 8);
                    ptr += 12;
                    GLStateManager.glStencilFunc(func, ref, mask);
                }
                case GLCommand.STENCIL_OP -> {
                    final int fail = memGetInt(ptr);
                    final int zfail = memGetInt(ptr + 4);
                    final int zpass = memGetInt(ptr + 8);
                    ptr += 12;
                    GLStateManager.glStencilOp(fail, zfail, zpass);
                }
                case GLCommand.TEX_PARAMETERI -> {
                    final int target = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final int param = memGetInt(ptr + 8);
                    ptr += 12;
                    GLStateManager.glTexParameteri(target, pname, param);
                }

                // === Four int commands ===
                case GLCommand.VIEWPORT -> {
                    final int x = memGetInt(ptr);
                    final int y = memGetInt(ptr + 4);
                    final int width = memGetInt(ptr + 8);
                    final int height = memGetInt(ptr + 12);
                    ptr += 16;
                    GLStateManager.glViewport(x, y, width, height);
                }
                case GLCommand.BLEND_FUNC -> {
                    final int srcRgb = memGetInt(ptr);
                    final int dstRgb = memGetInt(ptr + 4);
                    final int srcAlpha = memGetInt(ptr + 8);
                    final int dstAlpha = memGetInt(ptr + 12);
                    ptr += 16;
                    if (srcRgb == srcAlpha && dstRgb == dstAlpha) {
                        GLStateManager.glBlendFunc(srcRgb, dstRgb);
                    } else {
                        GLStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                    }
                }
                case GLCommand.COLOR_MASK -> {
                    final boolean r = memGetInt(ptr) != 0;
                    final boolean g = memGetInt(ptr + 4) != 0;
                    final boolean b = memGetInt(ptr + 8) != 0;
                    final boolean a = memGetInt(ptr + 12) != 0;
                    ptr += 16;
                    GLStateManager.glColorMask(r, g, b, a);
                }
                case GLCommand.STENCIL_FUNC_SEPARATE -> {
                    final int face = memGetInt(ptr);
                    final int func = memGetInt(ptr + 4);
                    final int ref = memGetInt(ptr + 8);
                    final int mask = memGetInt(ptr + 12);
                    ptr += 16;
                    GLStateManager.glStencilFuncSeparate(face, func, ref, mask);
                }
                case GLCommand.STENCIL_OP_SEPARATE -> {
                    final int face = memGetInt(ptr);
                    final int sfail = memGetInt(ptr + 4);
                    final int dpfail = memGetInt(ptr + 8);
                    final int dppass = memGetInt(ptr + 12);
                    ptr += 16;
                    GLStateManager.glStencilOpSeparate(face, sfail, dpfail, dppass);
                }

                // === Float commands ===
                case GLCommand.POINT_SIZE -> {
                    GLStateManager.glPointSize(memGetFloat(ptr));
                    ptr += 4;
                }
                case GLCommand.LINE_WIDTH -> {
                    GLStateManager.glLineWidth(memGetFloat(ptr));
                    ptr += 4;
                }
                case GLCommand.POLYGON_OFFSET -> {
                    final float factor = memGetFloat(ptr);
                    final float units = memGetFloat(ptr + 4);
                    ptr += 8;
                    GLStateManager.glPolygonOffset(factor, units);
                }
                case GLCommand.COLOR -> {
                    final float r = memGetFloat(ptr);
                    final float g = memGetFloat(ptr + 4);
                    final float b = memGetFloat(ptr + 8);
                    final float a = memGetFloat(ptr + 12);
                    ptr += 16;
                    GLStateManager.glColor4f(r, g, b, a);
                }
                case GLCommand.CLEAR_COLOR -> {
                    final float r = memGetFloat(ptr);
                    final float g = memGetFloat(ptr + 4);
                    final float b = memGetFloat(ptr + 8);
                    final float a = memGetFloat(ptr + 12);
                    ptr += 16;
                    GLStateManager.glClearColor(r, g, b, a);
                }
                case GLCommand.CLEAR_DEPTH -> {
                    final double depth = memGetDouble(ptr);
                    ptr += 8;
                    GLStateManager.glClearDepth(depth);
                }
                case GLCommand.BLEND_COLOR -> {
                    final float r = memGetFloat(ptr);
                    final float g = memGetFloat(ptr + 4);
                    final float b = memGetFloat(ptr + 8);
                    final float a = memGetFloat(ptr + 12);
                    ptr += 16;
                    GLStateManager.glBlendColor(r, g, b, a);
                }

                // === Mixed int+float commands ===
                case GLCommand.ALPHA_FUNC -> {
                    final int func = memGetInt(ptr);
                    final float ref = memGetFloat(ptr + 4);
                    ptr += 8;
                    GLStateManager.glAlphaFunc(func, ref);
                }
                case GLCommand.FOGF -> {
                    final int pname = memGetInt(ptr);
                    final float param = memGetFloat(ptr + 4);
                    ptr += 8;
                    GLStateManager.glFogf(pname, param);
                }
                case GLCommand.LIGHTF -> {
                    final int light = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final float param = memGetFloat(ptr + 8);
                    ptr += 12;
                    GLStateManager.glLightf(light, pname, param);
                }
                case GLCommand.LIGHT_MODELF -> {
                    final int pname = memGetInt(ptr);
                    final float param = memGetFloat(ptr + 4);
                    ptr += 8;
                    GLStateManager.glLightModelf(pname, param);
                }
                case GLCommand.LIGHTI -> {
                    final int light = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final int param = memGetInt(ptr + 8);
                    ptr += 12;
                    GLStateManager.glLighti(light, pname, param);
                }
                case GLCommand.LIGHT_MODELI -> {
                    final int pname = memGetInt(ptr);
                    final int param = memGetInt(ptr + 4);
                    ptr += 8;
                    GLStateManager.glLightModeli(pname, param);
                }
                case GLCommand.MATERIALF -> {
                    final int face = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final float param = memGetFloat(ptr + 8);
                    ptr += 12;
                    GLStateManager.glMaterialf(face, pname, param);
                }
                case GLCommand.TEX_PARAMETERF -> {
                    final int target = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final float param = memGetFloat(ptr + 8);
                    ptr += 12;
                    GLStateManager.glTexParameterf(target, pname, param);
                }

                // === Double commands ===
                case GLCommand.TRANSLATE -> {
                    final double x = memGetDouble(ptr);
                    final double y = memGetDouble(ptr + 8);
                    final double z = memGetDouble(ptr + 16);
                    ptr += 24;
                    GLStateManager.glTranslated(x, y, z);
                }
                case GLCommand.ROTATE -> {
                    final double angle = memGetDouble(ptr);
                    final double x = memGetDouble(ptr + 8);
                    final double y = memGetDouble(ptr + 16);
                    final double z = memGetDouble(ptr + 24);
                    ptr += 32;
                    GLStateManager.glRotated(angle, x, y, z);
                }
                case GLCommand.SCALE -> {
                    final double x = memGetDouble(ptr);
                    final double y = memGetDouble(ptr + 8);
                    final double z = memGetDouble(ptr + 16);
                    ptr += 24;
                    GLStateManager.glScaled(x, y, z);
                }
                case GLCommand.ORTHO -> {
                    final double left = memGetDouble(ptr);
                    final double right = memGetDouble(ptr + 8);
                    final double bottom = memGetDouble(ptr + 16);
                    final double top = memGetDouble(ptr + 24);
                    final double zNear = memGetDouble(ptr + 32);
                    final double zFar = memGetDouble(ptr + 40);
                    ptr += 48;
                    GLStateManager.glOrtho(left, right, bottom, top, zNear, zFar);
                }
                case GLCommand.FRUSTUM -> {
                    final double left = memGetDouble(ptr);
                    final double right = memGetDouble(ptr + 8);
                    final double bottom = memGetDouble(ptr + 16);
                    final double top = memGetDouble(ptr + 24);
                    final double zNear = memGetDouble(ptr + 32);
                    final double zFar = memGetDouble(ptr + 40);
                    ptr += 48;
                    GLStateManager.glFrustum(left, right, bottom, top, zNear, zFar);
                }

                // === Matrix commands ===
                case GLCommand.MULT_MATRIX -> {
                    // Mode-agnostic: just multiply current matrix
                    MATRIX_BUFFER.clear();
                    for (int i = 0; i < 16; i++) {
                        MATRIX_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    MATRIX_BUFFER.flip();
                    GLStateManager.glMultMatrix(MATRIX_BUFFER);
                }
                case GLCommand.LOAD_MATRIX -> {
                    MATRIX_BUFFER.clear();
                    for (int i = 0; i < 16; i++) {
                        MATRIX_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    MATRIX_BUFFER.flip();
                    GLStateManager.glLoadMatrix(MATRIX_BUFFER);
                }

                // === Buffer commands ===
                case GLCommand.FOG -> {
                    final int pname = memGetInt(ptr);
                    final int count = memGetInt(ptr + 4);
                    ptr += 8;
                    PARAMS_BUFFER.clear();
                    for (int i = 0; i < count; i++) {
                        PARAMS_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    PARAMS_BUFFER.flip();
                    GLStateManager.glFog(pname, PARAMS_BUFFER);
                }
                case GLCommand.LIGHT -> {
                    final int light = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final int count = memGetInt(ptr + 8);
                    ptr += 12;
                    PARAMS_BUFFER.clear();
                    for (int i = 0; i < count; i++) {
                        PARAMS_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    PARAMS_BUFFER.flip();
                    GLStateManager.glLight(light, pname, PARAMS_BUFFER);
                }
                case GLCommand.LIGHT_MODEL -> {
                    final int pname = memGetInt(ptr);
                    final int count = memGetInt(ptr + 4);
                    ptr += 8;
                    PARAMS_BUFFER.clear();
                    for (int i = 0; i < count; i++) {
                        PARAMS_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    PARAMS_BUFFER.flip();
                    GLStateManager.glLightModel(pname, PARAMS_BUFFER);
                }
                case GLCommand.MATERIAL -> {
                    final int face = memGetInt(ptr);
                    final int pname = memGetInt(ptr + 4);
                    final int count = memGetInt(ptr + 8);
                    ptr += 12;
                    PARAMS_BUFFER.clear();
                    for (int i = 0; i < count; i++) {
                        PARAMS_BUFFER.put(memGetFloat(ptr));
                        ptr += 4;
                    }
                    PARAMS_BUFFER.flip();
                    GLStateManager.glMaterial(face, pname, PARAMS_BUFFER);
                }

                // === Draw commands ===
                case GLCommand.DRAW_RANGE -> {
                    final int vboIndex = memGetInt(ptr);
                    final int start = memGetInt(ptr + 4);
                    final int count = memGetInt(ptr + 8);
                    final boolean hasBrightness = memGetInt(ptr + 12) != 0;
                    ptr += 16;
                    final VertexBuffer vbo = ownedVbos[vboIndex];
                    vbo.setupState();
                    vbo.draw(start, count);
                    vbo.cleanupState();
                }
                case GLCommand.DRAW_RANGE_RESTORE -> {
                    // Draw VBO range then restore GL current state from last vertex attributes
                    final int vboIndex = memGetInt(ptr);
                    final int start = memGetInt(ptr + 4);
                    final int count = memGetInt(ptr + 8);
                    final int flags = memGetInt(ptr + 12);

                    // Draw the VBO
                    final VertexBuffer vbo = ownedVbos[vboIndex];
                    vbo.setupState();
                    vbo.draw(start, count);
                    vbo.cleanupState();

                    // Restore attributes based on flags
                    if ((flags & FLAG_HAS_COLOR) != 0) {
                        final float r = memGetFloat(ptr + 16);
                        final float g = memGetFloat(ptr + 20);
                        final float b = memGetFloat(ptr + 24);
                        final float a = memGetFloat(ptr + 28);
                        GLStateManager.glColor4f(r, g, b, a);
                    }
                    if ((flags & FLAG_HAS_NORMALS) != 0) {
                        final float nx = memGetFloat(ptr + 32);
                        final float ny = memGetFloat(ptr + 36);
                        final float nz = memGetFloat(ptr + 40);
                        GLStateManager.glNormal3f(nx, ny, nz);
                    }
                    if ((flags & FLAG_HAS_TEXTURE) != 0) {
                        final float s = memGetFloat(ptr + 44);
                        final float t = memGetFloat(ptr + 48);
                        GLStateManager.glTexCoord2f(s, t);
                    }
                    ptr += 52;  // Skip full command size (56 - 4 for cmd already read)
                }
                case GLCommand.CALL_LIST -> {
                    final int listId = memGetInt(ptr);
                    ptr += 4;
                    DisplayListManager.glCallList(listId);
                }
                case GLCommand.DRAW_BUFFER -> {
                    GLStateManager.glDrawBuffer(memGetInt(ptr));
                    ptr += 4;
                }
                case GLCommand.DRAW_BUFFERS -> {
                    final int count = memGetInt(ptr);
                    ptr += 4;
                    DRAW_BUFFERS_BUFFER.clear();
                    for (int i = 0; i < count; i++) {
                        DRAW_BUFFERS_BUFFER.put(memGetInt(ptr + i * 4));
                    }
                    DRAW_BUFFERS_BUFFER.flip();
                    ptr += 4 * MAX_DRAW_BUFFERS;
                    GLStateManager.glDrawBuffers(DRAW_BUFFERS_BUFFER);
                }

                // === Complex object reference ===
                case GLCommand.COMPLEX_REF -> {
                    final int index = memGetInt(ptr);
                    ptr += 4;
                    ((DisplayListCommand) complexObjects[index]).execute();
                }

                default -> throw new IllegalStateException("Unknown command opcode: " + cmd);
            }
        }
    }
}
