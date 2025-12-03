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

    public static void processCommandForOptimization(int opcode, CommandBuffer raw, BufferTransformOptimizer opt, CommandBuffer out) {
        switch (opcode) {
            // === MODELVIEW matrix commands - accumulate instead of copying ===
            case GLCommand.TRANSLATE -> {
                final int mode = raw.readInt();
                final double x = raw.readDouble();
                final double y = raw.readDouble();
                final double z = raw.readDouble();
                if (mode == GL11.GL_MODELVIEW) {
                    opt.translate(x, y, z);
                } else {
                    out.writeTranslate(mode, x, y, z);
                }
            }
            case GLCommand.ROTATE -> {
                final int mode = raw.readInt();
                final double angle = raw.readDouble();
                final double x = raw.readDouble();
                final double y = raw.readDouble();
                final double z = raw.readDouble();
                if (mode == GL11.GL_MODELVIEW) {
                    opt.rotate(angle, x, y, z);
                } else {
                    out.writeRotate(mode, angle, x, y, z);
                }
            }
            case GLCommand.SCALE -> {
                final int mode = raw.readInt();
                final double x = raw.readDouble();
                final double y = raw.readDouble();
                final double z = raw.readDouble();
                if (mode == GL11.GL_MODELVIEW) {
                    opt.scale(x, y, z);
                } else {
                    out.writeScale(mode, x, y, z);
                }
            }
            case GLCommand.MULT_MATRIX -> {
                final int mode = raw.readInt();
                raw.readMatrix4f(tempMatrix);
                if (mode == GL11.GL_MODELVIEW) {
                    opt.multMatrix(tempMatrix);
                } else {
                    out.writeMultMatrix(mode, tempMatrix);
                }
            }
            case GLCommand.LOAD_IDENTITY -> {
                final int mode = raw.readInt();
                if (mode == GL11.GL_MODELVIEW) {
                    if (opt.checkAndClearAbsoluteMatrix()) {
                        out.writeLoadIdentity(mode);
                    }
                    opt.loadIdentity();
                } else {
                    out.writeLoadIdentity(mode);
                }
            }
            case GLCommand.LOAD_MATRIX -> {
                final int mode = raw.readInt();
                raw.readMatrix4f(tempMatrix);
                if (mode == GL11.GL_MODELVIEW) {
                    opt.emitPendingTransform(out);
                    out.writeLoadMatrix(mode, tempMatrix);
                    opt.loadIdentity();
                    opt.markAbsoluteMatrix();
                } else {
                    out.writeLoadMatrix(mode, tempMatrix);
                }
            }

            // === Stack operations - update optimizer state and copy ===
            case GLCommand.PUSH_MATRIX -> {
                final int mode = raw.readInt();
                if (mode == GL11.GL_MODELVIEW) {
                    opt.emitPendingTransform(out);
                    opt.push();
                }
                out.writePushMatrix(mode);
            }
            case GLCommand.POP_MATRIX -> {
                final int mode = raw.readInt();
                if (mode == GL11.GL_MODELVIEW) {
                    opt.pop();
                }
                out.writePopMatrix(mode);
            }

            // === Barriers - emit pending transform before copying ===
            case GLCommand.CALL_LIST -> {
                final int listId = raw.readInt();
                opt.emitPendingTransform(out);
                out.writeCallList(listId);
            }
            case GLCommand.PUSH_ATTRIB -> {
                final int mask = raw.readInt();
                if ((mask & GL11.GL_TRANSFORM_BIT) != 0) {
                    opt.emitPendingTransform(out);
                }
                out.writePushAttrib(mask);
            }

            // === All other commands - just copy ===
            default -> copyCommandData(opcode, raw, out);
        }
    }

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
            case GLCommand.LOAD_IDENTITY -> out.writeLoadIdentity(raw.readInt());
            case GLCommand.PUSH_MATRIX -> out.writePushMatrix(raw.readInt());
            case GLCommand.POP_MATRIX -> out.writePopMatrix(raw.readInt());
            case GLCommand.STENCIL_MASK -> out.writeStencilMask(raw.readInt());
            case GLCommand.DEPTH_MASK -> out.writeDepthMask(raw.readInt() != 0);

            // Two int commands
            case GLCommand.BIND_TEXTURE -> out.writeBindTexture(raw.readInt(), raw.readInt());
            case GLCommand.POLYGON_MODE -> out.writePolygonMode(raw.readInt(), raw.readInt());
            case GLCommand.COLOR_MATERIAL -> out.writeColorMaterial(raw.readInt(), raw.readInt());
            case GLCommand.LINE_STIPPLE -> out.writeLineStipple(raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_MASK_SEPARATE -> out.writeStencilMaskSeparate(raw.readInt(), raw.readInt());
            case GLCommand.FOGI -> out.writeFogi(raw.readInt(), raw.readInt());

            // Three int commands
            case GLCommand.STENCIL_FUNC -> out.writeStencilFunc(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_OP -> out.writeStencilOp(raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.TEX_PARAMETERI -> out.writeTexParameteri(raw.readInt(), raw.readInt(), raw.readInt());

            // Four int commands
            case GLCommand.VIEWPORT -> out.writeViewport(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.BLEND_FUNC -> out.writeBlendFunc(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.COLOR_MASK -> out.writeColorMask(raw.readInt() != 0, raw.readInt() != 0, raw.readInt() != 0, raw.readInt() != 0);
            case GLCommand.STENCIL_FUNC_SEPARATE -> out.writeStencilFuncSeparate(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());
            case GLCommand.STENCIL_OP_SEPARATE -> out.writeStencilOpSeparate(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt());

            // Float commands
            case GLCommand.POINT_SIZE -> out.writePointSize(raw.readFloat());
            case GLCommand.LINE_WIDTH -> out.writeLineWidth(raw.readFloat());
            case GLCommand.POLYGON_OFFSET -> out.writePolygonOffset(raw.readFloat(), raw.readFloat());
            case GLCommand.NORMAL -> out.writeNormal(raw.readFloat(), raw.readFloat(), raw.readFloat());
            case GLCommand.COLOR -> out.writeColor(raw.readFloat(), raw.readFloat(), raw.readFloat(), raw.readFloat());
            case GLCommand.CLEAR_COLOR -> out.writeClearColor(raw.readFloat(), raw.readFloat(), raw.readFloat(), raw.readFloat());

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
            case GLCommand.TRANSLATE -> out.writeTranslate(raw.readInt(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.ROTATE -> out.writeRotate(raw.readInt(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.SCALE -> out.writeScale(raw.readInt(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.ORTHO -> out.writeOrtho(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());
            case GLCommand.FRUSTUM -> out.writeFrustum(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readDouble());

            // Matrix commands
            case GLCommand.MULT_MATRIX -> { int mode = raw.readInt(); out.writeMultMatrix(mode, raw.readMatrix4f(tempMatrix)); }
            case GLCommand.LOAD_MATRIX -> { int mode = raw.readInt(); out.writeLoadMatrix(mode, raw.readMatrix4f(tempMatrix)); }

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

            // Draw/call commands
            case GLCommand.DRAW_RANGE -> out.writeDrawRange(raw.readInt(), raw.readInt(), raw.readInt(), raw.readInt() != 0);
            case GLCommand.CALL_LIST -> out.writeCallList(raw.readInt());

            // Complex object reference - transfer the object to output buffer
            case GLCommand.COMPLEX_REF -> {
                final int index = raw.readInt();
                final Object obj = raw.getComplexObject(index);
                out.writeComplexRef((DisplayListCommand) obj);
            }

            default -> LOGGER.warn("[CommandBufferProcessor] Unknown opcode in buffer copy: {}", opcode);
        }
    }

    /**
     * Lightweight transform optimizer for buffer-to-buffer processing.
     * Tracks accumulated MODELVIEW transform and emits MultMatrix commands when needed.
     */
    public static class BufferTransformOptimizer {
        private final Matrix4f accumulated = new Matrix4f();
        private final Matrix4f lastEmitted = new Matrix4f();
        private final Deque<Matrix4f> stack = new ArrayDeque<>();
        private boolean absoluteMatrixFlag = false;

        public BufferTransformOptimizer() {
            accumulated.identity();
            lastEmitted.identity();
        }

        public void translate(double x, double y, double z) {
            accumulated.translate((float) x, (float) y, (float) z);
        }

        public void rotate(double angle, double x, double y, double z) {
            accumulated.rotate((float) Math.toRadians(angle), (float) x, (float) y, (float) z);
        }

        public void scale(double x, double y, double z) {
            accumulated.scale((float) x, (float) y, (float) z);
        }

        public void multMatrix(Matrix4f m) {
            accumulated.mul(m);
        }

        public void loadIdentity() {
            accumulated.identity();
        }

        public void push() {
            stack.push(new Matrix4f(accumulated));
        }

        public void pop() {
            if (!stack.isEmpty()) {
                accumulated.set(stack.pop());
                lastEmitted.set(accumulated);
            } else {
                accumulated.identity();
                lastEmitted.identity();
            }
        }

        public boolean isIdentity() {
            return DisplayListManager.isIdentity(accumulated);
        }

        public void markAbsoluteMatrix() {
            absoluteMatrixFlag = true;
        }

        public boolean checkAndClearAbsoluteMatrix() {
            final boolean was = absoluteMatrixFlag;
            absoluteMatrixFlag = false;
            return was;
        }

        public void emitPendingTransform(CommandBuffer out) {
            if (!accumulated.equals(lastEmitted)) {
                emitTransformTo(out, accumulated);
            }
        }

        public void emitTransformTo(CommandBuffer out, Matrix4f target) {
            if (target.equals(lastEmitted)) {
                return;
            }

            if (DisplayListManager.isIdentity(lastEmitted)) {
                out.writeMultMatrix(GL11.GL_MODELVIEW, target);
            } else {
                final Matrix4f delta = new Matrix4f(lastEmitted).invert().mul(target);
                out.writeMultMatrix(GL11.GL_MODELVIEW, delta);
            }
            lastEmitted.set(target);
        }
    }
}
