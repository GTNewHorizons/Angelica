package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.BigVBO;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.BigVBOBuilder;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.CommandBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.OptimizationContext;
import io.netty.buffer.ByteBuf;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VBO-based display list emulation with command recording, transform collapsing, and format-based batching.
 *
 * <p><b>Transform Collapsing:</b> Consecutive transforms collapse into single MultMatrix commands,
 * emitted at barriers (draws, CallList, exit). Vertices stay canonical in VBOs. Handles nested
 * display lists correctly and maintains Push/Pop semantics.
 *
 * <p><b>Format-Based Batching:</b> Draws with same vertex format share a VBO via {@link BigVBO}.
 * Consecutive same-transform draws merge.
 * Delta transforms handled via {@link TransformOptimizer}.
 */
@UtilityClass
public class DisplayListManager {
    private static final Logger LOGGER = LogManager.getLogger("DisplayListManager");

    // -Dangelica.debugDisplayLists: disable transform collapsing and draw merging
    private static final boolean DEBUG_DISPLAY_LISTS = Boolean.getBoolean("angelica.debugDisplayLists");

    // -Dangelica.logDisplayListCompilation: log compiled display list commands
    private static final boolean LOG_DISPLAY_LIST_COMPILATION;

    static {
        LOG_DISPLAY_LIST_COMPILATION = Boolean.getBoolean("angelica.logDisplayListCompilation");
        if (LOG_DISPLAY_LIST_COMPILATION) {
            LogManager.getLogger("DisplayListManager").warn("Display list compilation logging ENABLED (-Dangelica.logDisplayListCompilation=true)");
        }
    }

    // Track which display list is currently being rendered
    @Getter private static int currentRenderingList = -1;

    private static final Matrix4f IDENTITY = new Matrix4f();

    public static boolean isIdentity(Matrix4f m) {
        return (m.properties() & Matrix4f.PROPERTY_IDENTITY) != 0 || m.equals(IDENTITY, 1e-6f);
    }

    /** Transform operation types for relative transform tracking. */
    public enum TransformOp { TRANSLATE, SCALE, ROTATE }

    /** Recording mode for display list compilation. */
    public enum RecordMode {
        NONE,               // Not recording
        COMPILE,            // GL_COMPILE: record only, don't execute
        COMPILE_AND_EXECUTE // GL_COMPILE_AND_EXECUTE: record and execute
    }

    // Display list compilation state (current/active context)
    private static int glListMode = 0;
    private static int glListId = -1;
    private static CommandRecorder currentRecorder = null;  // Command recorder (null when not recording)
    private static volatile Thread recordingThread = null;  // Thread that started recording (for thread-safety)
    private static List<AccumulatedDraw> accumulatedDraws = null;  // Accumulates quad draws for batching
    private static Matrix4fStack relativeTransform = null;  // Tracks relative transforms during compilation (with push/pop support)
    /**
     * -- GETTER --
     * Get the current matrix generation (for draw batching).
     */
    @Getter
    private static int matrixGeneration = 0;  // Increments when relativeTransform changes; used for draw batching
    private static int lastFlushedGeneration = -1;  // Generation at last flush; draws trigger flush when gen changes
    /**
     * -- GETTER --
     * Get the current state generation (for draw merging).
     */
    @Getter
    private static int stateGeneration = 0;  // Increments at draw barriers (state commands); used for draw merging
    private static Matrix4f lastFlushedTransform = null;  // The transform that was flushed; consecutive same-gen draws share this
    @Getter private static ImmediateModeRecorder immediateModeRecorder = null;  // Records glBegin/glEnd/glVertex during compilation
    private static StackTraceElement[] compilationStackTrace = null;  // For logging: captured at glNewList()

    // Debug logging: track sources of MULT_MATRIX commands and draw origins; only populated when LOG_DISPLAY_LIST_COMPILATION is true
    private static List<String> pendingTransformOps = null;  // Ops accumulated since last flush
    private static List<List<String>> multMatrixSources = null;  // Source ops for each MULT_MATRIX in raw buffer
    private static List<String> drawRangeSources = null;  // Source type for each DRAW_RANGE in final buffer

    // For flushMatrix() - reusable buffer for executing collapsed transforms
    private static final FloatBuffer flushMatrixBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);

    // Nested compilation support - stack of parent contexts
    private static final Deque<CompilationContext> compilationStack = new ArrayDeque<>();

    // Display list cache
    private static final Int2ObjectMap<CompiledDisplayList> displayListCache = new Int2ObjectOpenHashMap<>();

    /**
     * Compilation context for a single display list being compiled.
     * Used to support nested glNewList() calls (spec-violating but needed for mod compatibility).
     */
    @Desugar
    private record CompilationContext(
        int listId,
        int listMode,
        CommandRecorder recorder,
        List<AccumulatedDraw> draws,
        Matrix4fStack transform,
        ImmediateModeRecorder immediateRecorder,
        StackTraceElement[] stackTrace,

        // Debug logging fields (only used when LOG_DISPLAY_LIST_COMPILATION)
        List<String> pendingOps,
        List<List<String>> matrixSources,
        List<String> drawSources
    ) {}

    public static RecordMode getRecordMode() {
        if (currentRecorder == null || Thread.currentThread() != recordingThread) {
            return RecordMode.NONE;
        }
        return glListMode == GL11.GL_COMPILE_AND_EXECUTE ? RecordMode.COMPILE_AND_EXECUTE : RecordMode.COMPILE;
    }

    /** True only for the thread that started recording. */
    public static boolean isRecording() {
        return currentRecorder != null && Thread.currentThread() == recordingThread;
    }

    public static boolean isCompileAndExecute() {
        return glListMode == GL11.GL_COMPILE_AND_EXECUTE;
    }

    /** Emit accumulated transform as MultMatrix if non-identity, then reset. */
    public static boolean flushMatrix() {
        if (relativeTransform == null || isIdentity(relativeTransform)) {
            // Clear pending ops even if we don't emit - they were no-ops (identity)
            if (pendingTransformOps != null) {
                pendingTransformOps.clear();
            }
            return false;
        }

        // Save pending transform ops for logging (before we clear them)
        if (multMatrixSources != null && pendingTransformOps != null) {
            if (pendingTransformOps.isEmpty()) {
                LOGGER.warn("flushMatrix: non-identity transform with no tracked ops");
                multMatrixSources.add(Collections.singletonList("(unknown source)"));
            } else {
                multMatrixSources.add(new ArrayList<>(pendingTransformOps));
            }
            pendingTransformOps.clear();
        }

        // Record the collapsed MultMatrix command (for playback)
        if (currentRecorder != null) {
            currentRecorder.recordMultMatrix(relativeTransform);
        }

        // COMPILE_AND_EXECUTE: execute now. Bypass GLSM to avoid re-entering recording path.
        if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
            flushMatrixBuffer.clear();
            relativeTransform.get(flushMatrixBuffer);
            GL11.glMultMatrix(flushMatrixBuffer);
            GLStateManager.getMatrixStack().mul(relativeTransform);
        }

        // Reset to identity - we're now synchronized with GL
        relativeTransform.identity();

        return true;
    }

    public static void matrixBarrier() {
        flushMatrix();
    }

    public static int getRecordingListId() {
        return glListId;
    }

    public static int getListMode() {
        return glListMode;
    }

    public static int getCommandCount() {
        return currentRecorder != null ? currentRecorder.getCommandCount() : 0;
    }

    public static void trackDrawRangeSource(String source) {
        if (drawRangeSources != null) {
            drawRangeSources.add(source);
        }
    }

    // Draw barriers: state commands that prevent draw merging
    private static void drawBarrier() {
        stateGeneration++;
    }

    public static void recordEnable(int cap) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordEnable(cap);
    }

    public static void recordDisable(int cap) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDisable(cap);
    }

    // ==================== NON-BARRIER COMMANDS ====================
    // Clear commands don't affect subsequent draws, just clear buffers.

    public static void recordClear(int mask) {
        if (currentRecorder == null) return;
        currentRecorder.recordClear(mask);
    }

    public static void recordClearColor(float r, float g, float b, float a) {
        if (currentRecorder == null) return;
        currentRecorder.recordClearColor(r, g, b, a);
    }

    public static void recordClearDepth(double depth) {
        if (currentRecorder == null) return;
        currentRecorder.recordClearDepth(depth);
    }

    public static void recordClearStencil(int s) {
        if (currentRecorder == null) return;
        currentRecorder.recordClearStencil(s);
    }

    // ==================== MORE DRAW BARRIER COMMANDS ====================

    public static void recordBlendColor(float r, float g, float b, float a) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordBlendColor(r, g, b, a);
    }

    public static void recordColor(float r, float g, float b, float a) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordColor(r, g, b, a);
    }

    public static void recordColorMask(boolean r, boolean g, boolean b, boolean a) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordColorMask(r, g, b, a);
    }

    public static void recordDepthMask(boolean flag) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDepthMask(flag);
    }

    public static void recordFrontFace(int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordFrontFace(mode);
    }

    public static void recordDepthFunc(int func) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDepthFunc(func);
    }

    public static void recordBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void recordAlphaFunc(int func, float ref) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordAlphaFunc(func, ref);
    }

    public static void recordCullFace(int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordCullFace(mode);
    }

    public static void recordShadeModel(int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordShadeModel(mode);
    }

    public static void recordBindTexture(int target, int texture) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordBindTexture(target, texture);
    }

    public static void recordTexParameteri(int target, int pname, int param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordTexParameteri(target, pname, param);
    }

    public static void recordTexParameterf(int target, int pname, float param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordTexParameterf(target, pname, param);
    }

    public static void recordMatrixMode(int mode) {
        if (currentRecorder == null) return;
        matrixBarrier();  // Matrix barrier: flush and reset
        currentRecorder.recordMatrixMode(mode);
    }

    public static void recordPushMatrix() {
        if (currentRecorder == null) return;
        // Flush any pending delta, then record push.
        flushMatrix();
        currentRecorder.recordPushMatrix();
    }

    public static void recordPopMatrix() {
        if (currentRecorder == null) return;
        // Flush any pending delta, then record pop.
        flushMatrix();
        currentRecorder.recordPopMatrix();
    }

    public static void recordViewport(int x, int y, int width, int height) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordViewport(x, y, width, height);
    }

    public static void recordPointSize(float size) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordPointSize(size);
    }

    public static void recordLineWidth(float width) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLineWidth(width);
    }

    public static void recordLineStipple(int factor, int pattern) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLineStipple(factor, pattern);
    }

    public static void recordPolygonOffset(float factor, float units) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordPolygonOffset(factor, units);
    }

    public static void recordPolygonMode(int face, int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordPolygonMode(face, mode);
    }

    public static void recordColorMaterial(int face, int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordColorMaterial(face, mode);
    }

    public static void recordLogicOp(int opcode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLogicOp(opcode);
    }

    public static void recordActiveTexture(int texture) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordActiveTexture(texture);
    }

    public static void recordUseProgram(int program) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordUseProgram(program);
    }

    // PushAttrib saves state but doesn't change it - not a draw barrier
    public static void recordPushAttrib(int mask) {
        if (currentRecorder == null) return;
        currentRecorder.recordPushAttrib(mask);
    }

    // PopAttrib restores potentially any state - draw barrier
    public static void recordPopAttrib() {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordPopAttrib();
    }

    public static void recordFogf(int pname, float param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordFogf(pname, param);
    }

    public static void recordFogi(int pname, int param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordFogi(pname, param);
    }

    public static void recordHint(int target, int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordHint(target, mode);
    }

    public static void recordFog(int pname, java.nio.FloatBuffer params) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordFog(pname, params);
    }

    public static void recordLightf(int light, int pname, float param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLightf(light, pname, param);
    }

    public static void recordLighti(int light, int pname, int param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLighti(light, pname, param);
    }

    public static void recordLight(int light, int pname, java.nio.FloatBuffer params) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLight(light, pname, params);
    }

    public static void recordLightModelf(int pname, float param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLightModelf(pname, param);
    }

    public static void recordLightModeli(int pname, int param) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLightModeli(pname, param);
    }

    public static void recordLightModel(int pname, java.nio.FloatBuffer params) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordLightModel(pname, params);
    }

    public static void recordMaterialf(int face, int pname, float val) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordMaterialf(face, pname, val);
    }

    public static void recordMaterial(int face, int pname, java.nio.FloatBuffer params) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordMaterial(face, pname, params);
    }

    public static void recordStencilFunc(int func, int ref, int mask) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilFunc(func, ref, mask);
    }

    public static void recordStencilFuncSeparate(int face, int func, int ref, int mask) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilFuncSeparate(face, func, ref, mask);
    }

    public static void recordStencilOp(int fail, int zfail, int zpass) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilOp(fail, zfail, zpass);
    }

    public static void recordStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    public static void recordStencilMask(int mask) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilMask(mask);
    }

    public static void recordStencilMaskSeparate(int face, int mask) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordStencilMaskSeparate(face, mask);
    }

    public static void recordCallList(int listId) {
        if (currentRecorder == null) return;
        matrixBarrier();  // Matrix barrier: nested list has own transforms
        currentRecorder.recordCallList(listId);
    }

    public static void recordDrawBuffer(int mode) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDrawBuffer(mode);
    }

    public static void recordDrawBuffers(int count, int buf) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDrawBuffers(count, buf);
    }

    public static void recordDrawBuffers(int count, java.nio.IntBuffer bufs) {
        if (currentRecorder == null) return;
        drawBarrier();
        currentRecorder.recordDrawBuffers(count, bufs);
    }

    // Complex commands are texture uploads - not draw barriers
    public static void recordComplexCommand(DisplayListCommand cmd) {
        if (currentRecorder == null) return;
        currentRecorder.recordComplexCommand(cmd);
    }

    public static void recordLoadMatrix(Matrix4f matrix) {
        if (currentRecorder != null) currentRecorder.recordLoadMatrix(matrix);
    }

    public static void recordLoadIdentity() {
        if (currentRecorder != null) currentRecorder.recordLoadIdentity();
    }

    public static void recordDrawArrays(int mode, int start, int count) { if (currentRecorder != null) currentRecorder.recordDrawArrays(mode, start, count); }
    public static void recordBindVBO(int vbo) { if (currentRecorder != null) currentRecorder.recordBindVBO(vbo); }
    public static void recordBindVAO(int vao) { if (currentRecorder != null) currentRecorder.recordBindVAO(vao); }


    // recordOrtho/recordFrustum removed - these now accumulate into relativeTransform
    // via updateRelativeTransformOrtho/updateRelativeTransformFrustum

    /**
     * Add an immediate mode draw to the current display list compilation.
     * Called by GLStateManager.glEnd() when immediate mode geometry is ready.
     *
     * <p>This method captures the current transform and command position, creating
     * an AccumulatedDraw that will be interleaved correctly with other commands
     * during display list playback.
     *
     */
    public static void addImmediateModeDraw(DirectTessellator tessellator) {
        if (!tessellator.isEmpty()) {
            // Get relative transform (changes since glNewList, not absolute matrix state)
            addAccumulatedDraw(tessellator, relativeTransform, true);
        }
        tessellator.reset();
    }

    private static void applyTransformOp(Matrix4f m, float x, float y, float z, TransformOp op, Vector3f axis) {
        switch (op) {
            case TRANSLATE -> m.translate(x, y, z);
            case SCALE -> m.scale(x, y, z);
            case ROTATE -> { if (axis != null) m.rotate((float) Math.toRadians(x), axis); }
        }
    }

    public static void updateRelativeTransform(float x, float y, float z, TransformOp op, Vector3f rotationAxis) {
        if (relativeTransform == null) return;

        if (DEBUG_DISPLAY_LISTS) {
            final Matrix4f singleTransform = new Matrix4f();
            applyTransformOp(singleTransform, x, y, z, op, rotationAxis);
            if (currentRecorder != null) currentRecorder.recordMultMatrix(singleTransform);
            if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
                flushMatrixBuffer.clear();
                singleTransform.get(flushMatrixBuffer);
                GL11.glMultMatrix(flushMatrixBuffer);
                GLStateManager.getMatrixStack().mul(singleTransform);
            }
            return;
        }

        applyTransformOp(relativeTransform, x, y, z, op, rotationAxis);
        matrixGeneration++;

        if (pendingTransformOps != null) {
            final String log = switch (op) {
                case TRANSLATE -> String.format("glTranslatef(%.4f, %.4f, %.4f)", x, y, z);
                case SCALE -> String.format("glScalef(%.4f, %.4f, %.4f)", x, y, z);
                case ROTATE -> rotationAxis != null ? String.format("glRotatef(%.4f, %.4f, %.4f, %.4f)", x, rotationAxis.x, rotationAxis.y, rotationAxis.z) : null;
            };
            if (log != null) pendingTransformOps.add(log);
        }
    }

    /**
     * Update the relative transform with a matrix multiplication during display list compilation.
     * Called by GLStateManager.glMultMatrix().
     *
     * <p>In normal mode: accumulates transforms, emits collapsed MultMatrix at barriers.
     * <p>In debug mode: emits MultMatrix immediately (no collapsing).
     *
     * @param matrix The matrix to multiply
     */
    public static void updateRelativeTransform(Matrix4f matrix) {
        if (relativeTransform == null) {
            return;
        }

        if (DEBUG_DISPLAY_LISTS) {
            if (currentRecorder != null) {
                currentRecorder.recordMultMatrix(matrix);
            }

            if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
                flushMatrixBuffer.clear();
                matrix.get(flushMatrixBuffer);
                GL11.glMultMatrix(flushMatrixBuffer);
                GLStateManager.getMatrixStack().mul(matrix);
            }
            return;
        }

        relativeTransform.mul(matrix);
        matrixGeneration++;

        if (pendingTransformOps != null) {
            pendingTransformOps.add("glMultMatrixf(...)");
        }
    }

    // Reusable matrix for ortho/frustum computation
    private static final Matrix4f orthoFrustumTemp = new Matrix4f();

    /** Accumulate ortho projection into relativeTransform. */
    public static void updateRelativeTransformOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (relativeTransform == null) return;

        orthoFrustumTemp.identity().ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);

        if (DEBUG_DISPLAY_LISTS) {
            if (currentRecorder != null) currentRecorder.recordMultMatrix(orthoFrustumTemp);
            if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
                flushMatrixBuffer.clear();
                orthoFrustumTemp.get(flushMatrixBuffer);
                GL11.glMultMatrix(flushMatrixBuffer);
                GLStateManager.getMatrixStack().mul(orthoFrustumTemp);
            }
            return;
        }

        relativeTransform.mul(orthoFrustumTemp);
        matrixGeneration++;
        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glOrtho(%.4f, %.4f, %.4f, %.4f, %.4f, %.4f)", left, right, bottom, top, zNear, zFar));
        }
    }

    /** Accumulate frustum projection into relativeTransform. */
    public static void updateRelativeTransformFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (relativeTransform == null) return;

        orthoFrustumTemp.identity().frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);

        if (DEBUG_DISPLAY_LISTS) {
            if (currentRecorder != null) currentRecorder.recordMultMatrix(orthoFrustumTemp);
            if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
                flushMatrixBuffer.clear();
                orthoFrustumTemp.get(flushMatrixBuffer);
                GL11.glMultMatrix(flushMatrixBuffer);
                GLStateManager.getMatrixStack().mul(orthoFrustumTemp);
            }
            return;
        }

        relativeTransform.mul(orthoFrustumTemp);
        matrixGeneration++;
        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glFrustum(%.4f, %.4f, %.4f, %.4f, %.4f, %.4f)", left, right, bottom, top, zNear, zFar));
        }
    }

    /**
     * Reset the relative transform to identity during display list compilation.
     * Called by GLStateManager.glLoadMatrix() - after loading an absolute matrix,
     * subsequent transforms are relative to that loaded matrix (i.e., start from identity).
     */
    public static void resetRelativeTransform() {
        if (relativeTransform == null) {
            return;
        }
        relativeTransform.identity();
    }

    /**
     * Check if a display list exists (has been compiled and stored).
     * Checks both DisplayListManager's cache and VBOManager for GTNHLib compatibility.
     *
     * @param list The display list ID to check
     * @return true if the display list exists, false otherwise
     */
    public static boolean displayListExists(int list) {
        if (displayListCache.containsKey(list)) {
            return true;
        }
        // Check VBOManager for GTNHLib compatibility (negative IDs)
        if (list < -1) {
            return VBOManager.get(list) != null;
        }
        return false;
    }

    /**
     * Get a compiled display list from the cache.
     *
     * @param list The display list ID
     * @return The CompiledDisplayList, or null if not found
     */
    public static CompiledDisplayList getDisplayList(int list) {
        return displayListCache.get(list);
    }

    private static long time;

    /**
     * Start display list compilation.
     * Supports nested glNewList() calls (spec-violating but needed for mod compatibility).
     *   GL_COMPILE: Commands are recorded only (not executed), GLSM cache unchanged
     *   GL_COMPILE_AND_EXECUTE: Commands are recorded AND executed (GLSM cache updated)
     */
    public static void glNewList(int list, int mode) {
        time = System.nanoTime();
        // Assert main thread for display list compilation
        if (!Thread.currentThread().equals(GLStateManager.getMainThread())) {
            throw new IllegalStateException("Display list compilation must happen on main thread");
        }

        // Handle nested compilation - push current context onto stack
        final boolean isNested = glListMode > 0;

        if (isNested) {
            // Nested display list compilation violates OpenGL spec, but some of our optimizations require it
            // Save current compilation context and start fresh for nested list
            final CompilationContext parentContext = new CompilationContext(
                glListId, glListMode, currentRecorder, accumulatedDraws, relativeTransform, immediateModeRecorder,
                compilationStackTrace, pendingTransformOps, multMatrixSources, drawRangeSources
            );
            compilationStack.push(parentContext);
        }

        // Initialize fresh context for this (possibly nested) list
        glListId = list;
        glListMode = mode;
        recordingThread = Thread.currentThread();  // Track which thread is recording
        currentRecorder = new CommandRecorder();  // Create command recorder
        accumulatedDraws = new ArrayList<>(64);   // Fewer draws than commands typically
        relativeTransform = new Matrix4fStack(GLStateManager.MAX_MODELVIEW_STACK_DEPTH);
        // relativeTransform.identity();  // Track relative transforms from identity
        lastFlushedGeneration = -1;  // Reset so first draw triggers flush
        lastFlushedTransform = null;  // Will be set on first flush
        stateGeneration = 0;  // Reset state generation for fresh list
        immediateModeRecorder = new ImmediateModeRecorder();  // For glBegin/glEnd/glVertex
        compilationStackTrace = LOG_DISPLAY_LIST_COMPILATION ? Thread.currentThread().getStackTrace() : null;

        // Initialize debug logging fields (only when logging enabled)
        if (LOG_DISPLAY_LIST_COMPILATION) {
            pendingTransformOps = new ArrayList<>();
            multMatrixSources = new ArrayList<>();
            drawRangeSources = new ArrayList<>();
        } else {
            pendingTransformOps = null;
            multMatrixSources = null;
            drawRangeSources = null;
        }

        TessellatorManager.startCapturingDirect(new DirectTessellator((tessellator) -> {
            if (!tessellator.isEmpty()) {
                addAccumulatedDraw(tessellator, relativeTransform, false);
            }
            return true;
        }));
//
//        // We hijack display list compilation completely - no GL11.glNewList() calls
//        // During compile don't actually apply any changes to GL, but do track them
//        GLStateManager.getModelViewMatrix().pushMatrix();  // Save current model view matrix state
//        GLStateManager.pushState(GL11.GL_ALL_ATTRIB_BITS);
    }

    /**
     * End display list compilation and build optimized/unoptimized versions.
     */
    public static void glEndList() {
        if (glListMode == 0) {
            throw new RuntimeException("glEndList called outside of a display list!");
        }
        flushMatrix();
        final boolean isNested = !compilationStack.isEmpty();

        // Stop compiling mode (works for both root and nested lists now)
        TessellatorManager.stopCapturingDirect();

        final CompiledDisplayList compiled;
        // Create CompiledDisplayList with both unoptimized and optimized versions
        final CommandBuffer rawCommandBuffer = currentRecorder.getBuffer();
        final boolean hasCommands = !rawCommandBuffer.isEmpty();
        final boolean hasDraws = accumulatedDraws != null && !accumulatedDraws.isEmpty();

        if (hasCommands || hasDraws) {
            // Phase 1: Compile format-based VBOs (shared by both optimized and unoptimized paths)
            final BigVBO compiledBuffers = compileBigVBO(accumulatedDraws);

            // Build to CommandBuffer - optimize raw buffer to final buffer
            final CommandBuffer finalBuffer = new CommandBuffer();
            if (DEBUG_DISPLAY_LISTS) {
                CommandBufferBuilder.buildUnoptimizedFromRawBuffer(rawCommandBuffer, accumulatedDraws, finalBuffer);
            } else {
                CommandBufferBuilder.buildOptimizedFromRawBuffer(rawCommandBuffer, accumulatedDraws, glListId, finalBuffer);
            }

            // Free the recorder (and its buffer) after optimization
            currentRecorder.free();

            compiled = new CompiledDisplayList(finalBuffer.toBuffer(), finalBuffer.getComplexObjects(), compiledBuffers);
        } else {
            // Free the recorder even if empty
            if (currentRecorder != null) {
                currentRecorder.free();
            }
            // Empty display list - per OpenGL spec, still valid after glNewList/glEndList
            compiled = CompiledDisplayList.EMPTY;
        }
        // Store the compiled list (even if empty - an empty list is still a valid list)
        displayListCache.put(glListId, compiled);

        // Log compilation details if enabled (before context restoration changes glListId)
        if (LOG_DISPLAY_LIST_COMPILATION) {
            logCompiledDisplayList(glListId, compiled, compilationStackTrace);
        }

        // Handle nested compilation - restore parent context
        if (isNested) {
            final CompilationContext parentContext = compilationStack.pop();

            // Restore parent context
            glListId = parentContext.listId;
            glListMode = parentContext.listMode;
            currentRecorder = parentContext.recorder;
            accumulatedDraws = parentContext.draws;
            relativeTransform = parentContext.transform;
            immediateModeRecorder = parentContext.immediateRecorder;
            compilationStackTrace = parentContext.stackTrace;
            pendingTransformOps = parentContext.pendingOps;
            multMatrixSources = parentContext.matrixSources;
            drawRangeSources = parentContext.drawSources;

            // Note: TessellatorManager callback stack was popped by stopCompiling()
            // Parent's COMPILING callback is now active again
        } else {
            // Not nested - clear all compilation state
            currentRecorder = null;
            recordingThread = null;
            accumulatedDraws = null;
            relativeTransform = null;
            immediateModeRecorder = null;
            compilationStackTrace = null;
            pendingTransformOps = null;
            multMatrixSources = null;
            drawRangeSources = null;
            glListId = -1;
            glListMode = 0;
        }

        double diff = (System.nanoTime() - time) / 1_000_000d;
        System.out.println("Display lists (new) took " + diff + "ms.");

    }

    private static void addAccumulatedDraw(DirectTessellator tessellator, Matrix4f relativeTransform, boolean copyLast) {
        final Matrix4f currentTransform = new Matrix4f(relativeTransform);
        final int cmdIndex = getCommandCount();
        if (matrixGeneration != lastFlushedGeneration) {
            if (lastFlushedTransform == null) {
                lastFlushedTransform = new Matrix4f();
            }
            lastFlushedTransform.set(relativeTransform);
            flushMatrix();
            lastFlushedGeneration = matrixGeneration;
        }

        if (accumulatedDraws.isEmpty()) {
            accumulatedDraws.add(
                new AccumulatedDraw(
                    tessellator, currentTransform, cmdIndex, stateGeneration, copyLast
                )
            );
            return;
        }

        // Merge the previous draw call if possible
        final AccumulatedDraw previous = accumulatedDraws.get(accumulatedDraws.size() - 1);
        if (previous.format == tessellator.getVertexFormat() && previous.stateGeneration == stateGeneration) {
            previous.mergeDraw(tessellator, copyLast);
            return;
        }

        accumulatedDraws.add(
            new AccumulatedDraw(tessellator, currentTransform, cmdIndex, stateGeneration, copyLast)
        );
    }

    /**
     * Execute a compiled display list.
     */
    public static void glCallList(int list) {
        if (currentRecorder != null) {
            recordCallList(list);

            if (glListMode == GL11.GL_COMPILE) {
                return;
            }
        }

        final CompiledDisplayList compiled = displayListCache.get(list);
        if (compiled != null) {
            GLStateManager.trySyncProgram();
            final int prevList = currentRenderingList;
            currentRenderingList = list;
            compiled.render();
            currentRenderingList = prevList;
            return;
        }

        // Fall back to VBOManager or legacy display list
        if (list < 0) {
            // Negative IDs are VBOManager space (entity models, etc.)
            final VertexBuffer vbo = VBOManager.get(list);
            if (vbo != null) {
                vbo.render();
            }
            // Per OpenGL spec: if list is undefined, glCallList has no effect
        } else {
            // Positive IDs - fall back to native GL display lists
            // This happens for lists allocated but never compiled via glNewList
            GLStateManager.trySyncProgram();
            GL11.glCallList(list);
        }
    }

    /**
     * Delete display lists and free their VBO resources.
     */
    public static void glDeleteLists(int list, int range) {
        for (int i = list; i < list + range; i++) {
            CompiledDisplayList compiled = displayListCache.remove(i);
            if (compiled != null) {
                compiled.delete();
            }
        }
        // Also call legacy glDeleteLists for any lists we didn't handle
        GL11.glDeleteLists(list, range);
    }

    static BigVBO compileBigVBO(
        List<AccumulatedDraw> accumulatedDraws) {
        if (accumulatedDraws == null || accumulatedDraws.isEmpty()) {
            return BigVBO.emptyVBO();
        }

        // Compile each format's geometry into a single VBO
        final BigVBOBuilder builder = new BigVBOBuilder();
        for (AccumulatedDraw draw : accumulatedDraws) {
            builder.addDraw(draw.format, draw.drawMode, draw.drawBuffers);
        }
        return builder.build();
    }

    // ==================== DEBUG LOGGING ====================

    private static void logCompiledDisplayList(int listId, CompiledDisplayList compiled, StackTraceElement[] stackTrace) {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n========== Display List Compiled: ID=").append(listId).append(" ==========\n");

        // Identify probable source mod from stack trace
        sb.append("Source: ").append(identifySourceFromStackTrace(stackTrace)).append("\n");

        // Log commands if not empty
        if (compiled == CompiledDisplayList.EMPTY) {
            sb.append("Contents: EMPTY (no commands or draws)\n");
        } else {
            final ByteBuffer buffer = compiled.getCommandBuffer();
            if (buffer == null || buffer.limit() == 0) {
                sb.append("Contents: No commands\n");
            } else {
                sb.append("Commands (").append(buffer.limit()).append(" bytes):\n");
                // dumpCommandBuffer(buffer, compiled.getComplexObjects(), compiled.getOwnedVbos(), sb);
            }
        }

        // Log full stack trace
        sb.append("\nFull Stack Trace:\n");
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                sb.append("  at ").append(element.toString()).append("\n");
            }
        } else {
            sb.append("  (not captured)\n");
        }

        sb.append("========== End Display List ").append(listId).append(" ==========\n");
        LOGGER.debug(sb.toString());
    }

    private static String identifySourceFromStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return "Unknown (no stack trace)";
        }

        for (StackTraceElement element : stackTrace) {
            final String className = element.getClassName();

            // Skip Java/LWJGL/Angelica/Minecraft internals
            if (className.startsWith("java.") || className.startsWith("sun.") ||
                className.startsWith("jdk.") ||
                className.startsWith("org.lwjgl.") ||
                className.startsWith("com.gtnewhorizons.angelica.") ||
                className.startsWith("com.gtnewhorizon.gtnhlib.") ||
                className.startsWith("net.minecraft.") ||
                className.startsWith("net.minecraftforge.") ||
                className.contains("GLStateManager") ||
                className.contains("GradleStart") ||
                className.contains("launchwrapper") ||
                className.contains("retrofuturabootstrap")) {
                continue;
            }

            // Try to extract mod name from package
            final String modGuess = guessModFromClassName(className);
            return modGuess + " (" + element.getClassName() + "." + element.getMethodName() +
                   ":" + element.getLineNumber() + ")";
        }

        // Fallback: return the first non-internal frame with class info
        for (StackTraceElement element : stackTrace) {
            final String className = element.getClassName();
            if (!className.startsWith("java.") && !className.startsWith("sun.") && !className.startsWith("jdk.")) {
                return guessModFromClassName(className) + " (" + element + ")";
            }
        }
        return "Unknown";
    }

    private static String guessModFromClassName(String className) {
        final String lc = className.toLowerCase();

        // Common mod package patterns (use lowercase for matching)
        if (lc.contains("buildcraft")) return "BuildCraft";
        if (lc.contains("cofh")) return "CoFH/ThermalExpansion";
        if (lc.contains(".ic2.") || lc.startsWith("ic2.")) return "IndustrialCraft2";
        if (lc.contains("gregtech")) return "GregTech";
        if (lc.contains("thaumcraft")) return "Thaumcraft";
        if (lc.contains("forestry")) return "Forestry";
        if (lc.contains("appeng") || lc.contains(".ae2.")) return "Applied Energistics 2";
        if (lc.contains("enderio") || lc.contains("crazypants.ender")) return "EnderIO";
        if (lc.contains("mekanism")) return "Mekanism";
        if (lc.contains("tconstruct")) return "Tinkers Construct";
        if (lc.contains("chisel")) return "Chisel";
        if (lc.contains("opencomputers") || lc.contains("li.cil.oc")) return "OpenComputers";
        if (lc.contains("computercraft") || lc.contains("dan200.computer")) return "ComputerCraft";
        if (lc.contains("railcraft")) return "Railcraft";
        if (lc.contains("projectred") || lc.contains("mrtjpcore")) return "Project Red";
        if (lc.contains("botania")) return "Botania";
        if (lc.contains("extrautil")) return "ExtraUtilities";
        if (lc.contains("openblocks")) return "OpenBlocks";
        if (lc.contains("carpenters")) return "Carpenters Blocks";
        if (lc.contains("biomesoplenty")) return "Biomes O Plenty";
        if (lc.contains("natura")) return "Natura";
        if (lc.contains("twilightforest")) return "Twilight Forest";
        if (lc.contains("immersive")) return "Immersive Engineering";
        if (lc.contains("galacticraft")) return "Galacticraft";
        if (lc.contains("draconicevolution")) return "Draconic Evolution";
        if (lc.contains("net.minecraft")) return "Minecraft";

        // Extract first 2 package segments as fallback
        final String[] parts = className.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return className;
    }

    private static void dumpCommandBuffer(ByteBuffer buffer, Object[] complexObjects, VertexBuffer[] ownedVbos, StringBuilder sb) {
        if (buffer == null) return;

        final long basePtr = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress(buffer);
        long ptr = basePtr;
        final long end = basePtr + buffer.limit();
        int cmdNum = 0;
        int multMatrixIdx = 0;  // Index into multMatrixSources for source tracking
        int drawRangeIdx = 0;   // Index into drawRangeSources for source tracking

        while (ptr < end) {
            final int opcode = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr);
            final String cmdName = GLCommand.getName(opcode);
            sb.append("  ").append(cmdNum++).append(": ").append(cmdName);

            // Add command-specific details
            switch (opcode) {
                case GLCommand.ENABLE, GLCommand.DISABLE -> {
                    final int cap = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    sb.append("(").append(GLDebug.getCapabilityName(cap)).append(")");
                    ptr += 8;
                }
                case GLCommand.BIND_TEXTURE -> {
                    final int target = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    final int texture = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 8);
                    sb.append("(target=").append(target).append(", texture=").append(texture).append(")");
                    ptr += 12;
                }
                case GLCommand.DRAW_RANGE -> {
                    final int vboIdx = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    final int start = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 8);
                    final int count = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 12);
                    final int brightness = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 16);
                    sb.append("(vbo=").append(vboIdx).append(", start=").append(start)
                      .append(", count=").append(count).append(", brightness=").append(brightness != 0).append(")");
                    // Show draw source if available
                    if (drawRangeSources != null && drawRangeIdx < drawRangeSources.size()) {
                        sb.append(" [from: ").append(drawRangeSources.get(drawRangeIdx)).append("]");
                    }
                    drawRangeIdx++;
                    ptr += 20;
                }
                case GLCommand.MULT_MATRIX, GLCommand.LOAD_MATRIX -> {
                    // Show source ops if available (only for MULT_MATRIX from recording phase)
                    if (opcode == GLCommand.MULT_MATRIX && multMatrixSources != null && multMatrixIdx < multMatrixSources.size()) {
                        final List<String> sources = multMatrixSources.get(multMatrixIdx);
                        sb.append(" [from: ").append(String.join(" -> ", sources)).append("]");
                    }
                    multMatrixIdx++;
                    ptr += 68;  // cmd + 16 floats
                }
                case GLCommand.COLOR -> {
                    final float r = Float.intBitsToFloat(com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4));
                    final float g = Float.intBitsToFloat(com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 8));
                    final float b = Float.intBitsToFloat(com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 12));
                    final float a = Float.intBitsToFloat(com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 16));
                    sb.append("(").append(r).append(", ").append(g).append(", ").append(b).append(", ").append(a).append(")");
                    ptr += 20;
                }
                case GLCommand.DEPTH_MASK -> {
                    final int flag = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    sb.append("(").append(flag != 0).append(")");
                    ptr += 8;
                }
                case GLCommand.BLEND_FUNC -> {
                    final int srcRgb = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    final int dstRgb = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 8);
                    final int srcAlpha = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 12);
                    final int dstAlpha = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 16);
                    sb.append("(srcRgb=").append(srcRgb).append(", dstRgb=").append(dstRgb)
                      .append(", srcAlpha=").append(srcAlpha).append(", dstAlpha=").append(dstAlpha).append(")");
                    ptr += 20;
                }
                case GLCommand.CALL_LIST -> {
                    final int calledList = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    sb.append("(").append(calledList).append(")");
                    ptr += 8;
                }
                case GLCommand.MATRIX_MODE -> {
                    final int mode = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    sb.append("(").append(mode == GL11.GL_MODELVIEW ? "MODELVIEW" : mode == GL11.GL_PROJECTION ? "PROJECTION" : mode).append(")");
                    ptr += 8;
                }
                case GLCommand.PUSH_MATRIX, GLCommand.POP_MATRIX, GLCommand.LOAD_IDENTITY -> ptr += 4;
                case GLCommand.COMPLEX_REF -> {
                    final int idx = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 4);
                    final Object obj = complexObjects != null && idx < complexObjects.length ? complexObjects[idx] : null;
                    sb.append("(idx=").append(idx).append(", type=").append(obj != null ? obj.getClass().getSimpleName() : "null").append(")");
                    ptr += 8;
                }
                default -> {
                    // Skip based on command size (from CompiledDisplayList.getCommandSize logic)
                    ptr += getCommandSize(opcode, ptr);
                }
            }
            sb.append("\n");
        }
    }

    /**
     * Get the size of a command in bytes for buffer iteration.
     */
    private static int getCommandSize(int cmd, long ptr) {
        return switch (cmd) {
            case GLCommand.LOAD_IDENTITY, GLCommand.PUSH_MATRIX, GLCommand.POP_MATRIX -> 4;
            case GLCommand.ENABLE, GLCommand.DISABLE, GLCommand.CLEAR, GLCommand.CLEAR_STENCIL,
                 GLCommand.CULL_FACE, GLCommand.DEPTH_FUNC, GLCommand.SHADE_MODEL, GLCommand.LOGIC_OP,
                 GLCommand.MATRIX_MODE, GLCommand.ACTIVE_TEXTURE, GLCommand.USE_PROGRAM,
                 GLCommand.PUSH_ATTRIB, GLCommand.POP_ATTRIB, GLCommand.STENCIL_MASK,
                 GLCommand.DEPTH_MASK, GLCommand.FRONT_FACE, GLCommand.POINT_SIZE, GLCommand.LINE_WIDTH,
                 GLCommand.CALL_LIST, GLCommand.COMPLEX_REF, GLCommand.DRAW_BUFFER -> 8;
            case GLCommand.BIND_TEXTURE, GLCommand.POLYGON_MODE, GLCommand.COLOR_MATERIAL,
                 GLCommand.LINE_STIPPLE, GLCommand.STENCIL_MASK_SEPARATE, GLCommand.FOGI,
                 GLCommand.HINT, GLCommand.POLYGON_OFFSET, GLCommand.ALPHA_FUNC, GLCommand.FOGF,
                 GLCommand.LIGHT_MODELF, GLCommand.LIGHT_MODELI, GLCommand.CLEAR_DEPTH -> 12;
            case GLCommand.STENCIL_FUNC, GLCommand.STENCIL_OP, GLCommand.TEX_PARAMETERI,
                 GLCommand.LIGHTF, GLCommand.LIGHTI, GLCommand.MATERIALF, GLCommand.TEX_PARAMETERF,
                 GLCommand.NORMAL -> 16;
            case GLCommand.VIEWPORT, GLCommand.BLEND_FUNC, GLCommand.COLOR_MASK,
                 GLCommand.STENCIL_FUNC_SEPARATE, GLCommand.STENCIL_OP_SEPARATE,
                 GLCommand.COLOR, GLCommand.CLEAR_COLOR, GLCommand.BLEND_COLOR,
                 GLCommand.DRAW_RANGE -> 20;
            case GLCommand.TRANSLATE, GLCommand.SCALE -> 28;
            case GLCommand.ROTATE -> 36;
            case GLCommand.ORTHO, GLCommand.FRUSTUM -> 52;
            case GLCommand.MULT_MATRIX, GLCommand.LOAD_MATRIX -> 68;
            case GLCommand.FOG, GLCommand.LIGHT_MODEL -> {
                final int count = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 8);
                yield 12 + count * 4;
            }
            case GLCommand.LIGHT, GLCommand.MATERIAL -> {
                final int count = com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt(ptr + 12);
                yield 16 + count * 4;
            }
            case GLCommand.DRAW_BUFFERS -> 40;
            default -> 8;  // Default to 8 bytes to avoid infinite loop
        };
    }

}
