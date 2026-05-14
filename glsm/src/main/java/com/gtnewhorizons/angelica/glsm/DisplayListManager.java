package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.CallbackTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectDrawCallback;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorCallback;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.tessellator.VertexTransformCallback;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.DisplayListVBO;
import com.gtnewhorizons.angelica.glsm.recording.DisplayListVBOBuilder;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatchBuilder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import com.mojang.brigadier.Command;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.experimental.UtilityClass;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;

/**
 * VBO-based display list emulation with command recording, transform collapsing, and format-based batching.
 *
 * <p><b>Transform Collapsing:</b> Consecutive transforms collapse into single MultMatrix commands,
 * emitted at barriers (draws, CallList, exit). Vertices stay canonical in VBOs. Handles nested
 * display lists correctly and maintains Push/Pop semantics.
 *
 * <p><b>Format-Based Batching:</b> Draws with same vertex format share a VBO via {@link DisplayListVBO}.
 * Consecutive same-transform draws merge.
 */
@UtilityClass
public class DisplayListManager {
    // -Dangelica.debugDisplayLists: disable transform collapsing and draw merging
    private static final boolean DEBUG_DISPLAY_LISTS;

    // -Dangelica.logDisplayListCompilation: log compiled display list commands
    private static final boolean LOG_DISPLAY_LIST_COMPILATION;

    static {
        DEBUG_DISPLAY_LISTS = Boolean.getBoolean("angelica.debugDisplayLists");

        LOG_DISPLAY_LIST_COMPILATION = Boolean.getBoolean("angelica.logDisplayListCompilation");
        if (LOG_DISPLAY_LIST_COMPILATION) {
            GLStateManager.LOGGER.warn("Display list compilation logging ENABLED (-Dangelica.logDisplayListCompilation=true)");
        }
    }


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
    private static AccumulatedDraw pendingDraw = null;
    private static DisplayListCallback transformCallback = null;
    private static int relativeTransformType;

    private static final int TRANSFORM_SCALE = 0x1; // Can be extracted to a glScale easily
    private static final int TRANSFORM_TRANSLATE = 0x2; // Can be extracted to a glTranslate easily
    private static final int TRANSFORM_COMPLEX = 0x4; // Cannot easily be extracted, will record a mult matrix call.

    private static StackTraceElement[] compilationStackTrace = null;  // For logging: captured at glNewList()

    // Debug logging: track sources of MULT_MATRIX commands and draw origins; only populated when LOG_DISPLAY_LIST_COMPILATION is true
    private static List<String> pendingTransformOps = null;  // Ops accumulated since last flush
    private static List<List<String>> multMatrixSources = null;  // Source ops for each MULT_MATRIX in raw buffer
    private static List<String> drawRangeSources = null;  // Source type for each DRAW_RANGE in final buffer

    // Nested compilation support - stack of parent contexts
    private static final Deque<CompilationContext> compilationStack = new ArrayDeque<>();

    // Display list cache
    private static final Int2ObjectMap<CompiledDisplayList> displayListCache = new Int2ObjectOpenHashMap<>();

    /**
     * Compilation context for a single display list being compiled.
     * Used to support nested glNewList() calls (spec-violating but needed for mod compatibility).
     */
    private record CompilationContext(
        int listId,
        int listMode,
        CommandRecorder recorder,
        List<AccumulatedDraw> draws,
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

    public static CommandRecorder pauseRecording() {
        final CommandRecorder r = currentRecorder;
        currentRecorder = null;
        return r;
    }

    public static void resumeRecording(CommandRecorder r) {
        currentRecorder = r;
    }


    private static final Vector3f transformVector = new Vector3f();

    static void flushAll() {
        drawBarrier();
        flushMatrix();
    }

    /**
     * Emit accumulated transform as MultMatrix if non-identity, then reset.
     */
    static void flushMatrix() {
        if (transformCallback.isIdentity()) {
            // Clear pending ops even if we don't emit - they were no-ops (identity)
            if (pendingTransformOps != null) {
                pendingTransformOps.clear();
            }
            return;
        }


        // Save pending transform ops for logging (before we clear them)
        if (multMatrixSources != null && pendingTransformOps != null) {
            if (pendingTransformOps.isEmpty()) {
                GLStateManager.LOGGER.warn("flushMatrix: non-identity transform with no tracked ops");
                multMatrixSources.add(Collections.singletonList("(unknown source)"));
            } else {
                multMatrixSources.add(new ArrayList<>(pendingTransformOps));
            }
            pendingTransformOps.clear();
        }

        // Record the collapsed MultMatrix command (for playback)
        // Always put a draw barrier BEFORE flushing the matrix (the transforms are already baked into the current draw)
        drawBarrier();
        if (relativeTransformType == TRANSFORM_SCALE) {
            currentRecorder.writeScale(transformCallback.getScale(transformVector));
        } else if (relativeTransformType == TRANSFORM_TRANSLATE) {
            currentRecorder.writeTranslate(transformCallback.getTranslation(transformVector));
        } else {
            currentRecorder.writeMultMatrix(transformCallback.getReadMatrix());
        }
        if (glListMode == GL11.GL_COMPILE_AND_EXECUTE) {
            GLStateManager.applyMultMatrix(transformCallback.getReadMatrix());
        }


        // Reset to identity - we're now synchronized with GL
        resetRelativeTransform();
    }

    public static int getRecordingListId() {
        return glListId;
    }

    public static int getListMode() {
        return glListMode;
    }

    public static void trackDrawRangeSource(String source) {
        if (drawRangeSources != null) {
            drawRangeSources.add(source);
        }
    }


    // Draw barriers: state commands that prevent draw merging
    static void drawBarrier() {
        if (pendingDraw != null) {
            emitDrawRangeToBuffer(pendingDraw, currentRecorder, accumulatedDraws.size() - 1);
            pendingDraw = null;
        }
    }

    private static void emitDrawRangeToBuffer(
        AccumulatedDraw draw,
        CommandRecorder out,
        int vboIdx) {

        if (draw.restoreData != null) {
            out.writeDrawRangeRestore(vboIdx, draw.restoreData);
            return;
        }

        // Write the draw range command
        out.writeDrawRange(vboIdx);
    }

    public static void recordEnable(int cap) {
        drawBarrier();
        currentRecorder.writeEnable(cap);
    }

    public static void recordDisable(int cap) {
        drawBarrier();
        currentRecorder.writeDisable(cap);
    }

    // ==================== NON-BARRIER COMMANDS ====================
    // Clear commands don't affect subsequent draws, just clear buffers.

    public static void recordClear(int mask) {
        drawBarrier(); // glClear changes the FBO values, needs a draw barrier.
        currentRecorder.writeClear(mask);
    }

    public static void recordClearColor(float r, float g, float b, float a) {
        currentRecorder.writeClearColor(r, g, b, a);
    }

    public static void recordClearDepth(double depth) {
        currentRecorder.writeClearDepth(depth);
    }

    public static void recordClearStencil(int s) {
        currentRecorder.writeClearStencil(s);
    }

    // ==================== MORE DRAW BARRIER COMMANDS ====================

    public static void recordBlendColor(float r, float g, float b, float a) {
        drawBarrier();
        currentRecorder.writeBlendColor(r, g, b, a);
    }

    public static void recordColor(float r, float g, float b, float a) {
        drawBarrier();
        currentRecorder.writeColor(r, g, b, a);
        if (ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setColor(r, g, b, a);
        }
    }

    public static void recordColorMask(boolean r, boolean g, boolean b, boolean a) {
        drawBarrier();
        currentRecorder.writeColorMask(r, g, b, a);
    }

    public static void recordDepthMask(boolean flag) {
        drawBarrier();
        currentRecorder.writeDepthMask(flag);
    }

    public static void recordFrontFace(int mode) {
        drawBarrier();
        currentRecorder.writeFrontFace(mode);
    }

    public static void recordDepthFunc(int func) {
        drawBarrier();
        currentRecorder.writeDepthFunc(func);
    }

    public static void recordBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        drawBarrier();
        currentRecorder.writeBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void recordAlphaFunc(int func, float ref) {
        drawBarrier();
        currentRecorder.writeAlphaFunc(func, ref);
    }

    public static void recordCullFace(int mode) {
        drawBarrier();
        currentRecorder.writeCullFace(mode);
    }

    public static void recordShadeModel(int mode) {
        drawBarrier();
        currentRecorder.writeShadeModel(mode);
    }

    public static void recordBindTexture(int target, int texture) {
        drawBarrier();
        currentRecorder.writeBindTexture(target, texture);
    }

    public static void recordTexParameteri(int target, int pname, int param) {
        drawBarrier();
        currentRecorder.writeTexParameteri(target, pname, param);
    }

    public static void recordTexParameterf(int target, int pname, float param) {
        drawBarrier();
        currentRecorder.writeTexParameterf(target, pname, param);
    }

    public static void recordMatrixMode(int mode) {
        flushMatrix();  // Matrix barrier: flush and reset
        transformCallback.setMatrixMode(mode);
        currentRecorder.writeMatrixMode(mode);
    }

    public static void recordPushMatrix() {
        // Flush any pending delta, then record push.
        flushMatrix();
        currentRecorder.writePushMatrix();
    }

    public static void recordPopMatrix() {
        // Discard any transformations & flush any pending draw operations
        drawBarrier();
        resetRelativeTransform();
        currentRecorder.writePopMatrix();
    }

    public static void recordViewport(int x, int y, int width, int height) {
        drawBarrier();
        currentRecorder.writeViewport(x, y, width, height);
    }

    public static void recordScissor(int x, int y, int width, int height) {
        drawBarrier();
        currentRecorder.writeScissor(x, y, width, height);
    }

    public static void recordPointSize(float size) {
        drawBarrier();
        currentRecorder.writePointSize(size);
    }

    public static void recordLineWidth(float width) {
        drawBarrier();
        currentRecorder.writeLineWidth(width);
    }

    public static void recordLineStipple(int factor, int pattern) {
        drawBarrier();
        currentRecorder.writeLineStipple(factor, pattern);
    }

    public static void recordPolygonOffset(float factor, float units) {
        drawBarrier();
        currentRecorder.writePolygonOffset(factor, units);
    }

    public static void recordPolygonMode(int face, int mode) {
        drawBarrier();
        currentRecorder.writePolygonMode(face, mode);
    }

    public static void recordColorMaterial(int face, int mode) {
        drawBarrier();
        currentRecorder.writeColorMaterial(face, mode);
    }

    public static void recordLogicOp(int opcode) {
        drawBarrier();
        currentRecorder.writeLogicOp(opcode);
    }

    public static void recordActiveTexture(int texture) {
        currentRecorder.writeActiveTexture(texture);
    }

    public static void recordUseProgram(int program) {
        drawBarrier();
        currentRecorder.writeUseProgram(program);
    }

    // PushAttrib saves state but doesn't change it - not a draw barrier
    public static void recordPushAttrib(int mask) {
        currentRecorder.writePushAttrib(mask);
    }

    // PopAttrib restores potentially any state - draw barrier
    public static void recordPopAttrib() {
        drawBarrier();
        currentRecorder.writePopAttrib();
    }

    public static void recordFogf(int pname, float param) {
        drawBarrier();
        currentRecorder.writeFogf(pname, param);
    }

    public static void recordFogi(int pname, int param) {
        drawBarrier();
        currentRecorder.writeFogi(pname, param);
    }

    public static void recordHint(int target, int mode) {
        drawBarrier();
        currentRecorder.writeHint(target, mode);
    }

    public static void recordFog(int pname, java.nio.FloatBuffer params) {
        drawBarrier();
        currentRecorder.writeFog(pname, params);
    }

    public static void recordLightf(int light, int pname, float param) {
        drawBarrier();
        currentRecorder.writeLightf(light, pname, param);
    }

    public static void recordLighti(int light, int pname, int param) {
        drawBarrier();
        currentRecorder.writeLighti(light, pname, param);
    }

    public static void recordLight(int light, int pname, java.nio.FloatBuffer params) {
        drawBarrier();
        currentRecorder.writeLight(light, pname, params);
    }

    public static void recordLightModelf(int pname, float param) {
        drawBarrier();
        currentRecorder.writeLightModelf(pname, param);
    }

    public static void recordLightModeli(int pname, int param) {
        drawBarrier();
        currentRecorder.writeLightModeli(pname, param);
    }

    public static void recordLightModel(int pname, java.nio.FloatBuffer params) {
        drawBarrier();
        currentRecorder.writeLightModel(pname, params);
    }

    public static void recordMaterialf(int face, int pname, float val) {
        drawBarrier();
        currentRecorder.writeMaterialf(face, pname, val);
    }

    public static void recordMaterial(int face, int pname, java.nio.FloatBuffer params) {
        drawBarrier();
        currentRecorder.writeMaterial(face, pname, params);
    }

    public static void recordClipPlane(int plane, double a, double b, double c, double d) {
        drawBarrier();
        currentRecorder.writeClipPlane(plane, a, b, c, d);
    }

    public static void recordStencilFunc(int func, int ref, int mask) {
        drawBarrier();
        currentRecorder.writeStencilFunc(func, ref, mask);
    }

    public static void recordStencilFuncSeparate(int face, int func, int ref, int mask) {
        drawBarrier();
        currentRecorder.writeStencilFuncSeparate(face, func, ref, mask);
    }

    public static void recordStencilOp(int fail, int zfail, int zpass) {
        drawBarrier();
        currentRecorder.writeStencilOp(fail, zfail, zpass);
    }

    public static void recordStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        drawBarrier();
        currentRecorder.writeStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    public static void recordStencilMask(int mask) {
        drawBarrier();
        currentRecorder.writeStencilMask(mask);
    }

    public static void recordStencilMaskSeparate(int face, int mask) {
        drawBarrier();
        currentRecorder.writeStencilMaskSeparate(face, mask);
    }

    public static void recordCallList(int listId) {
        // Flush all pending draws & transformations
        flushAll();
        currentRecorder.writeCallList(listId);
    }

    public static void recordDrawBuffer(int mode) {
        drawBarrier();
        currentRecorder.writeDrawBuffer(mode);
    }

    public static void recordDrawBuffers(int count, int buf) {
        drawBarrier();
        currentRecorder.writeDrawBuffers(count, buf);
    }

    public static void recordDrawBuffers(int count, java.nio.IntBuffer bufs) {
        drawBarrier();
        currentRecorder.writeDrawBuffers(count, bufs);
    }

    public static void recordComplexCommand(DisplayListCommand cmd) {
        flushAll(); //TODO
        currentRecorder.writeComplexCommand(cmd);
    }

    public static void recordIndexedDrawCapture(IndexedDrawCapture capture) {
        flushAll(); //TODO
        currentRecorder.writeIndexedDrawCapture(capture);
    }

    public static void recordLoadMatrix(Matrix4f matrix) {
        drawBarrier();
        resetRelativeTransform();
        currentRecorder.writeLoadMatrix(matrix);
    }

    public static void recordLoadIdentity() {
        drawBarrier();
        resetRelativeTransform();
        currentRecorder.writeLoadIdentity();

    }

    public static void recordBindVBO(int vbo) {
        drawBarrier();
        currentRecorder.writeBindVBO(vbo);
    }

    public static void recordBindVAO(int vao) {
        drawBarrier();
        currentRecorder.writeBindVAO(vao);
    }


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
            addAccumulatedDraw(tessellator, tessellator.getVertexFormat() != DefaultVertexFormat.POSITION);
        }
        tessellator.reset();
    }

    public static void applyMatrixTranslation(float x, float y, float z) {
        transformCallback.translate(x, y, z);
        relativeTransformType |= TRANSFORM_TRANSLATE;

        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glTranslatef(%.4f, %.4f, %.4f)", x, y, z));
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
        }
    }

    public static void applyMatrixScale(float x, float y, float z) {
        transformCallback.scale(x, y, z);
        relativeTransformType |= TRANSFORM_SCALE;

        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glScalef(%.4f, %.4f, %.4f)", x, y, z));
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
        }
    }

    /**
     * Records a matrix rotation.
     * <p>
     * Requires the angle to be in radians & the coordinates to be normalized.
     */
    public static void applyMatrixRotation(float rad, float x, float y, float z) {
        transformCallback.rotate(rad, x, y, z);
        relativeTransformType |= TRANSFORM_COMPLEX;

        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glRotatef(%.4f, %.4f, %.4f, %.4f)", Math.toDegrees(rad), x, y, z));
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
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
        transformCallback.multMatrix(matrix);
        relativeTransformType |= TRANSFORM_COMPLEX;

        if (pendingTransformOps != null) {
            pendingTransformOps.add("glMultMatrixf(...)");
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
        }
    }

    // Reusable matrix for ortho/frustum computation
    private static final Matrix4f orthoFrustumTemp = new Matrix4f();

    public static void updateRelativeTransformOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        orthoFrustumTemp.identity().ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);

        transformCallback.multMatrix(orthoFrustumTemp);
        relativeTransformType |= TRANSFORM_COMPLEX;

        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glOrtho(%.4f, %.4f, %.4f, %.4f, %.4f, %.4f)", left, right, bottom, top, zNear, zFar));
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
        }
    }

    public static void updateRelativeTransformFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        orthoFrustumTemp.identity().frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);

        transformCallback.multMatrix(orthoFrustumTemp);
        relativeTransformType |= TRANSFORM_COMPLEX;

        if (pendingTransformOps != null) {
            pendingTransformOps.add(String.format("glFrustum(%.4f, %.4f, %.4f, %.4f, %.4f, %.4f)", left, right, bottom, top, zNear, zFar));
        }

        if (DEBUG_DISPLAY_LISTS) {
            flushMatrix();
        }
    }

    /**
     * Reset the relative transform to identity during display list compilation.
     * Called by GLStateManager.glLoadMatrix() - after loading an absolute matrix,
     * subsequent transforms are relative to that loaded matrix (i.e., start from identity).
     */
    static void resetRelativeTransform() {
        transformCallback.setIdentity();
        relativeTransformType = 0;
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

    /**
     * Start display list compilation.
     * Supports nested glNewList() calls (spec-violating but needed for mod compatibility).
     *   GL_COMPILE: Commands are recorded only (not executed), GLSM cache unchanged
     *   GL_COMPILE_AND_EXECUTE: Commands are recorded AND executed (GLSM cache updated)
     */
    public static void glNewList(int list, int mode) {
        // Handle nested compilation - push current context onto stack
        final boolean isNested = glListMode > 0;

        if (isNested) {
            flushAll();
            // Nested display list compilation violates OpenGL spec, but some of our optimizations require it
            // Save current compilation context and start fresh for nested list
            final CompilationContext parentContext = new CompilationContext(
                glListId, glListMode, currentRecorder, accumulatedDraws,
                compilationStackTrace, pendingTransformOps, multMatrixSources, drawRangeSources
            );
            compilationStack.push(parentContext);
        }

        // Initialize fresh context for this (possibly nested) list
        glListId = list;
        glListMode = mode;
        recordingThread = Thread.currentThread();  // Track which thread is recording
        currentRecorder = new CommandRecorder();  // Create command recorder
        accumulatedDraws = new ArrayList<>(8);   // Fewer draws than commands typically
        transformCallback = new DisplayListCallback();
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

        TessellatorManager.startCapturingDirect(transformCallback);
    }

    /**
     * End display list compilation and build optimized/unoptimized versions.
     */
    public static void glEndList() {
        if (glListMode == 0) {
            throw new RuntimeException("glEndList called outside of a display list!");
        }

        final boolean isNested = !compilationStack.isEmpty();

        // Stop compiling mode (works for both root and nested lists now)
        TessellatorManager.stopCapturingDirect();

        flushAll();

        // Reset back to MODELVIEW (default state)
        if (transformCallback.getMatrixMode() != GL11.GL_MODELVIEW) {
            recordMatrixMode(GL11.GL_MODELVIEW);
        }

        final CompiledDisplayList compiled;
        // Create CompiledDisplayList with both unoptimized and optimized versions
        final CommandRecorder commandBuffer = currentRecorder;
        final boolean hasCommands = !commandBuffer.isEmpty();

        if (hasCommands) {
            final DisplayListVBO compiledBuffers = new DisplayListVBOBuilder().addDraws(accumulatedDraws).build();
            try {
                final IndexedDrawBatchBuilder indexedBuilder = commandBuffer.getIndexedDraws();
                final List<IndexedDrawBatch> indexedBatches;
                if (indexedBuilder.isEmpty()) {
                    indexedBatches = Collections.emptyList();
                } else {
                    final CommandRecorder paused = pauseRecording();
                    try {
                        indexedBatches = indexedBuilder.build();
                    } finally {
                        try {
                            for (IndexedDrawCapture c : indexedBuilder.getCaptures()) {
                                c.freeBuffers();
                            }
                        } finally {
                            resumeRecording(paused);
                        }
                    }
                }

                compiled = new CompiledDisplayList(commandBuffer.finish(), commandBuffer.getComplexObjects(), compiledBuffers, indexedBatches);
            } catch (Exception e) {
                GLStateManager.LOGGER.error("Encountered a fatal issue while trying to compile a display list.");
                e.printStackTrace();
                commandBuffer.delete();
                return;
            }
        } else {
            // Free the recorder even if empty
            commandBuffer.delete();
            // Empty display list - per OpenGL spec, still valid after glNewList/glEndList
            compiled = CompiledDisplayList.EMPTY;
        }
        final CompiledDisplayList previous = displayListCache.put(glListId, compiled);
        if (previous != null && previous != CompiledDisplayList.EMPTY && previous != compiled) {
            previous.delete();
        }

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
            pendingDraw = null;
            compilationStackTrace = null;
            pendingTransformOps = null;
            multMatrixSources = null;
            drawRangeSources = null;
            glListId = -1;
            glListMode = 0;
        }
    }

    private static void addAccumulatedDraw(DirectTessellator tessellator, boolean copyLast) {
        if (pendingDraw == null) {
            createNewDraw(tessellator, copyLast);
            return;
        }
        if (pendingDraw.drawMode != tessellator.drawMode
            || pendingDraw.format != tessellator.getVertexFormat()
            || isContinuous(tessellator.drawMode)
        ) {
            drawBarrier();
            createNewDraw(tessellator, copyLast);
            return;
        }
        pendingDraw.mergeDraw(tessellator, copyLast);
    }

    private static void createNewDraw(DirectTessellator tessellator, boolean copyLast) {
        pendingDraw = new AccumulatedDraw(
            tessellator, copyLast
        );
        accumulatedDraws.add(pendingDraw);
        if (DEBUG_DISPLAY_LISTS) {
            drawBarrier();
        }
    }

    /**
     * Draw modes that rely on the previous vertex data cannot be merged currently.
     * It is possible to merge them using Index Buffers, but currently unimplemented.
     */
    private static boolean isContinuous(int drawMode) {
        return drawMode == GL11.GL_TRIANGLE_STRIP || drawMode == GL11.GL_TRIANGLE_FAN
            || drawMode == GL11.GL_LINE_STRIP || drawMode == GL11.GL_LINE_LOOP
            || drawMode == GL11.GL_QUAD_STRIP || drawMode == GL11.GL_POLYGON;
    }

    /**
     * Execute a compiled display list.
     */
    public static void glCallList(int list) {
        if (currentRecorder != null) {
            recordCallList(list);

            if (getListMode() != GL11.GL_COMPILE) {
                // Don't record the GL calls, we already recorded glCallList
                final CommandRecorder recorder = currentRecorder;
                currentRecorder = null;

                executeDisplayList(list);

                currentRecorder = recorder;
            }
            return;
        }

        executeDisplayList(list);
    }

    private static void executeDisplayList(int list) {
        final CompiledDisplayList compiled = displayListCache.get(list);
        if (compiled != null) {
            compiled.render(list);
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
        }
        // An uncached positive-ID list = no-op (per OpenGL spec)
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
    }

    // ==================== DEBUG LOGGING ====================

    public static void logCompiledDisplayList(int listId, CompiledDisplayList compiled, StackTraceElement[] stackTrace) {
        GLStateManager.LOGGER.info(getCompiledDisplayListString(listId, compiled, stackTrace));
    }

    public static String getCompiledDisplayListString(int listId, CompiledDisplayList compiled, StackTraceElement[] stackTrace) {
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
                dumpCommandBuffer(buffer, compiled, sb);
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
        return sb.toString();
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

    private static void dumpCommandBuffer(ByteBuffer buffer, CompiledDisplayList compiledList, StringBuilder sb) {
        if (buffer == null) return;

        DisplayListCommand[] complexObjects = compiledList.getComplexObjects();
        DisplayListVBO vbos = compiledList.getOwnedVbos();
        final long basePtr = memAddress(buffer);
        long ptr = basePtr;
        final long end = basePtr + buffer.limit();
        int cmdNum = 0;
        int drawRangeIdx = 0;   // Index into drawRangeSources for source tracking

        try {
            while (ptr < end) {
                final int opcode = memGetInt(ptr);
                final String cmdName = GLCommand.getName(opcode);
                sb.append("  ").append(cmdNum++).append(": ").append(cmdName);

                // Add command-specific details
                switch (opcode) {
                    case GLCommand.ENABLE, GLCommand.DISABLE -> {
                        final int cap = memGetInt(ptr + 4);
                        sb.append("(").append(GLDebug.getCapabilityName(cap)).append(")");
                    }
                    case GLCommand.BIND_TEXTURE -> {
                        final int target = memGetInt(ptr + 4);
                        final int texture = memGetInt(ptr + 8);
                        sb.append("(target=").append(target).append(", texture=").append(texture).append(")");
                    }
                    case GLCommand.DRAW_RANGE -> {
                        final int vboIdx = memGetInt(ptr + 4);
                        final DisplayListVBO.SubVBO vbo = vbos.getVBO(vboIdx);
                        sb.append("(vbo=").append(vboIdx)
                            .append(", drawMode=").append(vbo.getDrawMode())
                            .append(", start=").append(vbo.getStart())
                            .append(", count=").append(vbo.getCount())
                            .append(")");
                        // Show draw source if available
                        if (drawRangeSources != null && drawRangeIdx < drawRangeSources.size()) {
                            sb.append(" [from: ").append(drawRangeSources.get(drawRangeIdx)).append("]");
                        }
                        drawRangeIdx++;
                    }
                    case GLCommand.DRAW_RANGE_RESTORE -> {
                        final int vboIdx = memGetInt(ptr + 4);
                        final int start = memGetInt(ptr + 8);
                        final int count = memGetInt(ptr + 12);
                        final int brightness = memGetInt(ptr + 16);
                        sb.append("(vbo=").append(vboIdx).append(", start=").append(start)
                            .append(", count=").append(count).append(", brightness=").append(brightness != 0).append(")");
                        // Show draw source if available
                        if (drawRangeSources != null && drawRangeIdx < drawRangeSources.size()) {
                            sb.append(" [from: ").append(drawRangeSources.get(drawRangeIdx)).append("]");
                        }
                        drawRangeIdx++;
                    }
                    case GLCommand.MULT_MATRIX, GLCommand.LOAD_MATRIX -> {
                        sb.append("(");
                        for (int i = 0; i < 16; i++) {
                            float value = MemoryUtilities.memGetFloat(ptr + 4 + i * 4);
                            sb.append(i == 0 ? value : ", " + value);
                        }
                        sb.append(")");
                    }
                    case GLCommand.SCALE, GLCommand.TRANSLATE -> {
                        final float x = memGetFloat(ptr + 4);
                        final float y = memGetFloat(ptr + 8);
                        final float z = memGetFloat(ptr + 12);
                        sb.append("(x=").append(x)
                            .append(", y=").append(y)
                            .append(", z=").append(z)
                            .append(")");
                    }
                    case GLCommand.COLOR -> {
                        final float r = Float.intBitsToFloat(memGetInt(ptr + 4));
                        final float g = Float.intBitsToFloat(memGetInt(ptr + 8));
                        final float b = Float.intBitsToFloat(memGetInt(ptr + 12));
                        final float a = Float.intBitsToFloat(memGetInt(ptr + 16));
                        sb.append("(").append(r).append(", ").append(g).append(", ").append(b).append(", ").append(a).append(")");
                    }
                    case GLCommand.DEPTH_MASK -> {
                        final int flag = memGetInt(ptr + 4);
                        sb.append("(").append(flag != 0).append(")");
                    }
                    case GLCommand.BLEND_FUNC -> {
                        final int srcRgb = memGetInt(ptr + 4);
                        final int dstRgb = memGetInt(ptr + 8);
                        final int srcAlpha = memGetInt(ptr + 12);
                        final int dstAlpha = memGetInt(ptr + 16);
                        sb.append("(srcRgb=").append(srcRgb).append(", dstRgb=").append(dstRgb)
                            .append(", srcAlpha=").append(srcAlpha).append(", dstAlpha=").append(dstAlpha).append(")");
                    }
                    case GLCommand.CALL_LIST -> {
                        final int calledList = memGetInt(ptr + 4);
                        sb.append("(").append(calledList).append(")");
                    }
                    case GLCommand.MATRIX_MODE -> {
                        final int mode = memGetInt(ptr + 4);
                        sb.append("(").append(mode == GL11.GL_MODELVIEW ? "MODELVIEW" : mode == GL11.GL_PROJECTION ? "PROJECTION" : mode).append(")");
                    }
                    case GLCommand.COMPLEX_REF -> {
                        final int idx = memGetInt(ptr + 4);
                        final Object obj = complexObjects != null && idx < complexObjects.length ? complexObjects[idx] : null;
                        sb.append("(idx=").append(idx).append(", type=").append(obj != null ? obj.getClass().getSimpleName() : "null").append(")");
                    }
                }
                ptr += GLCommand.getCommandSize(opcode, ptr);
                sb.append("\n");
            }
        } catch (Exception e) {
            sb.append("Unknown commands.\n");
        }
    }

    private static final class DisplayListCallback extends VertexTransformCallback {

        @Override
        public boolean onDraw(CallbackTessellator tessellator) {
            if (!tessellator.isEmpty()) {
                addAccumulatedDraw(tessellator, false);
            }
            return true;
        }
    }
}
