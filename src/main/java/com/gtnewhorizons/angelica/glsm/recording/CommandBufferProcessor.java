package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public final class CommandBufferProcessor {
    private static final Logger LOGGER = LogManager.getLogger("CommandBufferProcessor");
    private static final Matrix4f tempMatrix = new Matrix4f();

    private CommandBufferProcessor() {}

    /**
     * Copy a complete command (opcode already read) from raw to final buffer.
     */
    public static void copyCommand(CommandBuffer raw, CommandBuffer out) {
        final int opcode = raw.readInt();
        copyCommandData(opcode, raw, out);
    }

    /**
     * Copy command data based on opcode (opcode already consumed from raw).
     */
    public static void copyCommandData(int opcode, CommandBuffer raw, CommandBuffer out) {
        switch (opcode) {
            // Single int commands
            case GLCommand.ENABLE -> out.writeEnable(raw.readInt());
            case GLCommand.DISABLE -> out.writeDisable(raw.readInt());
            case GLCommand.CLEAR -> out.writeClear(raw.readInt());
            case GLCommand.CLEAR_STENCIL -> out.writeClearStencil(raw.readInt());
            case GLCommand.CULL_FACE -> out.writeCullFace(raw.readInt());
            case GLCommand.DEPTH_FUNC -> out.writeDepthFunc(raw.readInt());
            case GLCommand.SHADE_MODEL -> out.writeShadeModel(raw.readInt());
            case GLCommand.LOGIC_OP -> out.writeLogicOp(raw.readInt());
            case GLCommand.MATRIX_MODE -> out.writeMatrixMode(raw.readInt());
            case GLCommand.ACTIVE_TEXTURE -> out.writeActiveTexture(raw.readInt());
            case GLCommand.USE_PROGRAM -> out.writeUseProgram(raw.readInt());
            case GLCommand.PUSH_ATTRIB -> out.writePushAttrib(raw.readInt());
            case GLCommand.POP_ATTRIB -> { raw.readInt(); out.writePopAttrib(); }
            case GLCommand.LOAD_IDENTITY -> out.writeLoadIdentity();
            case GLCommand.PUSH_MATRIX -> out.writePushMatrix();
            case GLCommand.POP_MATRIX -> out.writePopMatrix();
            case GLCommand.STENCIL_MASK -> out.writeStencilMask(raw.readInt());
            case GLCommand.DEPTH_MASK -> out.writeDepthMask(raw.readInt() != 0);
            case GLCommand.FRONT_FACE -> out.writeFrontFace(raw.readInt());

            // Two int commands
            case GLCommand.BIND_TEXTURE -> out.writeBindTexture(raw.readInt(), raw.readInt());
            case GLCommand.POLYGON_MODE -> out.writePolygonMode(raw.readInt(), raw.readInt());
            case GLCommand.COLOR_MATERIAL -> out.writeColorMaterial(raw.readInt(), raw.readInt());
            case GLCommand.LINE_STIPPLE -> out.writeLineStipple(raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_MASK_SEPARATE -> out.writeStencilMaskSeparate(raw.readInt(), raw.readInt());
            case GLCommand.FOGI -> out.writeFogi(raw.readInt(), raw.readInt());
            case GLCommand.HINT -> out.writeHint(raw.readInt(), raw.readInt());

            // Three int commands
            case GLCommand.STENCIL_FUNC -> out.writeStencilFunc(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_OP -> out.writeStencilOp(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.TEX_PARAMETERI -> out.writeTexParameteri(raw.readInt(), raw.readInt(), raw.readInt());

            // Four int commands
            case GLCommand.VIEWPORT -> out.writeViewport(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.SCISSOR -> out.writeScissor(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.BLEND_FUNC -> out.writeBlendFunc(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.COLOR_MASK -> out.writeColorMask(raw.readInt() != 0, raw.readInt() != 0, raw.readInt() != 0, raw.readInt() != 0);
            case GLCommand.STENCIL_FUNC_SEPARATE -> out.writeStencilFuncSeparate(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_OP_SEPARATE -> out.writeStencilOpSeparate(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());

            // Float commands
            case GLCommand.POINT_SIZE -> out.writePointSize(raw.readFloat());
            case GLCommand.LINE_WIDTH -> out.writeLineWidth(raw.readFloat());
            case GLCommand.POLYGON_OFFSET -> out.writePolygonOffset(raw.readFloat(), raw.readFloat());
            case GLCommand.COLOR -> out.writeColor(raw.readFloat(), raw.readFloat(), raw.readFloat(), raw.readFloat());
            case GLCommand.CLEAR_COLOR -> out.writeClearColor(raw.readFloat(), raw.readFloat(), raw.readFloat(), raw.readFloat());

            // Single double commands
            case GLCommand.CLEAR_DEPTH -> out.writeClearDepth(raw.readDouble());

            // Four float commands
            case GLCommand.BLEND_COLOR -> out.writeBlendColor(raw.readFloat(), raw.readFloat(), raw.readFloat(), raw.readFloat());

            // Mixed int+float commands
            case GLCommand.ALPHA_FUNC -> out.writeAlphaFunc(raw.readInt(), raw.readFloat());
            case GLCommand.FOGF -> out.writeFogf(raw.readInt(), raw.readFloat());
            case GLCommand.LIGHTF -> out.writeLightf(raw.readInt(), raw.readInt(), raw.readFloat());
            case GLCommand.LIGHT_MODELF -> out.writeLightModelf(raw.readInt(), raw.readFloat());
            case GLCommand.LIGHTI -> out.writeLighti(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.LIGHT_MODELI -> out.writeLightModeli(raw.readInt(), raw.readInt());
            case GLCommand.MATERIALF -> out.writeMaterialf(raw.readInt(), raw.readInt(), raw.readFloat());
            case GLCommand.TEX_PARAMETERF -> out.writeTexParameterf(raw.readInt(), raw.readInt(), raw.readFloat());

            // Double commands
            case GLCommand.TRANSLATE -> out.writeTranslate(raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.ROTATE -> out.writeRotate(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.SCALE -> out.writeScale(raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.ORTHO -> out.writeOrtho(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.FRUSTUM -> out.writeFrustum(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());

            // Matrix commands
            case GLCommand.MULT_MATRIX -> { out.writeMultMatrix(raw.readMatrix4f(tempMatrix)); }
            case GLCommand.LOAD_MATRIX -> { out.writeLoadMatrix(raw.readMatrix4f(tempMatrix)); }

            // FloatBuffer commands (inline 4 floats max)
            case GLCommand.FOG -> {
                final int pname = raw.readInt();
                final int count = raw.readInt();
                final FloatBuffer fb = FloatBuffer.allocate(count);
                for (int i = 0; i < count; i++) fb.put(raw.readFloat());
                fb.flip();
                out.writeFog(pname, fb);
            }
            case GLCommand.LIGHT -> {
                final int light = raw.readInt();
                final int pname = raw.readInt();
                final int count = raw.readInt();
                final FloatBuffer fb = FloatBuffer.allocate(count);
                for (int i = 0; i < count; i++) fb.put(raw.readFloat());
                fb.flip();
                out.writeLight(light, pname, fb);
            }
            case GLCommand.LIGHT_MODEL -> {
                final int pname = raw.readInt();
                final int count = raw.readInt();
                final FloatBuffer fb = FloatBuffer.allocate(count);
                for (int i = 0; i < count; i++) fb.put(raw.readFloat());
                fb.flip();
                out.writeLightModel(pname, fb);
            }
            case GLCommand.MATERIAL -> {
                final int face = raw.readInt();
                final int pname = raw.readInt();
                final int count = raw.readInt();
                final FloatBuffer fb = FloatBuffer.allocate(count);
                for (int i = 0; i < count; i++) fb.put(raw.readFloat());
                fb.flip();
                out.writeMaterial(face, pname, fb);
            }

            // Clip plane command (4 doubles)
            case GLCommand.CLIP_PLANE -> {
                final int plane = raw.readInt();
                final double a = raw.readDouble();
                final double b = raw.readDouble();
                final double c = raw.readDouble();
                final double d = raw.readDouble();
                out.writeClipPlane(plane, a, b, c, d);
            }

            // Draw/call commands
            case GLCommand.DRAW_RANGE -> out.writeDrawRange(raw.readInt());
            case GLCommand.CALL_LIST -> out.writeCallList(raw.readInt());
            case GLCommand.DRAW_ARRAYS -> out.writeDrawArrays(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.DRAW_ELEMENTS -> out.writeDrawElements(raw.readInt(), raw.readInt(), raw.readInt(), raw.readLong());
            case GLCommand.BIND_VBO -> out.writeBindVBO(raw.readInt());
            case GLCommand.BIND_VAO -> out.writeBindVAO(raw.readInt());

            // Complex object reference - transfer the object to output buffer
            case GLCommand.COMPLEX_REF -> {
                final int index = raw.readInt();
                final Object obj = raw.getComplexObject(index);
                out.writeComplexRef((DisplayListCommand) obj);
            }

            default -> LOGGER.warn("[CommandBufferProcessor] Unknown opcode in buffer copy: {}", opcode);
        }
    }
}
