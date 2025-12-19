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
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.OptimizationContext;
import io.netty.buffer.ByteBuf;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages display list compilation, caching, and playback.
 * Handles VBO-based display list emulation with command recording and batching.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Display list lifecycle (glNewList, glEndList, glCallList, glDeleteLists)</li>
 *   <li>Command recording and accumulation during compilation</li>
 *   <li>Building optimized (batched) and unoptimized (nested-safe) command lists</li>
 *   <li>Transform tracking and collapsing for optimal GL call counts</li>
 *   <li>Cache management for compiled display lists</li>
 * </ul>
 *
 * <h3>Architecture: Transform Collapsing</h3>
 * <p>The optimization strategy collapses consecutive MODELVIEW transforms into single MultMatrix
 * commands, emitting them at "barriers" (draws, CallList, exit). This approach:</p>
 * <ul>
 *   <li>Keeps vertices canonical (untransformed) in VBOs</li>
 *   <li>Properly handles nested display lists (CallList) - GL state is correct before sub-list</li>
 *   <li>Reduces GL calls (multiple transforms → one MultMatrix)</li>
 *   <li>Maintains proper Push/Pop stack semantics</li>
 * </ul>
 *
 * <h4>Transform Tracking State</h4>
 * <ul>
 *   <li><b>accumulated:</b> Total relative transform (what GL matrix should be)</li>
 *   <li><b>lastEmitted:</b> What we've emitted (what GL matrix is)</li>
 *   <li><b>stack:</b> Saved accumulated values at Push points</li>
 * </ul>
 *
 * <h4>Batching Strategy</h4>
 * <p>Format-based batching: All draws with the same vertex format share a single VBO via
 * {@link FormatBuffer}. Each draw's position within the VBO is tracked as a {@link DrawRange}.
 * Consecutive draws with the same transform are merged into single ranges. Transforms are
 * handled via delta-based emission using {@link TransformOptimizer}.</p>
 *
 * @see com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList
 * @see com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw
 * @see com.gtnewhorizons.angelica.glsm.recording.commands.DrawRangeCmd
 */
@UtilityClass
public class DisplayListManager {
    private static final Logger LOGGER = LogManager.getLogger("DisplayListManager");

    /**
     * Debug flag to enable building unoptimized display list commands.
     * When false (default), unoptimized commands are not built
     * When true, unoptimized commands are built for debugging GL behavior.
     * Set via: -Dangelica.debugDisplayLists=true
     */
    private static final boolean DEBUG_DISPLAY_LISTS = Boolean.getBoolean("angelica.debugDisplayLists");

    // Track which display list is currently being rendered
    @Getter private static int currentRenderingList = -1;

    /**
     * Check if we're currently rendering a display list.
     * Used by format overrides to avoid interfering with display list VBOs.
     */
    public static boolean isRenderingDisplayList() {
        return currentRenderingList != -1;
    }

    public static boolean isIdentity(Matrix4f m) {
        return (m.properties() & Matrix4f.PROPERTY_IDENTITY) != 0;
    }

    /** Transform operation types for relative transform tracking. */
    public enum TransformOp { TRANSLATE, SCALE, ROTATE }

    // Display list compilation state (current/active context)
    private static int glListMode = 0;
    private static int glListId = -1;
    private static CommandRecorder currentRecorder = null;  // Command recorder (null when not recording)
    private static volatile Thread recordingThread = null;  // Thread that started recording (for thread-safety)
    private static List<AccumulatedDraw> accumulatedDraws = null;  // Accumulates quad draws for batching
    private static Matrix4fStack relativeTransform = null;  // Tracks relative transforms during compilation (with push/pop support)
    @Getter private static ImmediateModeRecorder immediateModeRecorder = null;  // Records glBegin/glEnd/glVertex during compilation

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
        ImmediateModeRecorder immediateRecorder
    ) {}

    /**
     * Check if the current thread is recording a display list.
     * Only returns true for the thread that started recording.
     * This prevents other threads from having their GL calls captured.
     */
    public static boolean isRecording() {
        return currentRecorder != null && Thread.currentThread() == recordingThread;
    }

    /**
     * Get the ID of the display list currently being recorded.
     * @return The list ID, or -1 if not recording
     */
    public static int getRecordingListId() {
        return glListId;
    }

    /**
     * Get the current display list mode (GL_COMPILE or GL_COMPILE_AND_EXECUTE).
     * @return The current list mode, or 0 if not recording
     */
    public static int getListMode() {
        return glListMode;
    }

    /**
     * Get the current command count (for draw position tracking).
     * @return The current command position
     */
    public static int getCommandCount() {
        return currentRecorder != null ? currentRecorder.getCommandCount() : 0;
    }

    // ==================== Recording Method Delegates ====================
    // These delegate to the current CommandRecorder during compilation.

    public static void recordEnable(int cap) { if (currentRecorder != null) currentRecorder.recordEnable(cap); }
    public static void recordDisable(int cap) { if (currentRecorder != null) currentRecorder.recordDisable(cap); }
    public static void recordClear(int mask) { if (currentRecorder != null) currentRecorder.recordClear(mask); }
    public static void recordClearColor(float r, float g, float b, float a) { if (currentRecorder != null) currentRecorder.recordClearColor(r, g, b, a); }
    public static void recordClearDepth(double depth) { if (currentRecorder != null) currentRecorder.recordClearDepth(depth); }
    public static void recordBlendColor(float r, float g, float b, float a) { if (currentRecorder != null) currentRecorder.recordBlendColor(r, g, b, a); }
    public static void recordClearStencil(int s) { if (currentRecorder != null) currentRecorder.recordClearStencil(s); }
    public static void recordColor(float r, float g, float b, float a) { if (currentRecorder != null) currentRecorder.recordColor(r, g, b, a); }
    public static void recordColorMask(boolean r, boolean g, boolean b, boolean a) { if (currentRecorder != null) currentRecorder.recordColorMask(r, g, b, a); }
    public static void recordDepthMask(boolean flag) { if (currentRecorder != null) currentRecorder.recordDepthMask(flag); }
    public static void recordFrontFace(int mode) { if (currentRecorder != null) currentRecorder.recordFrontFace(mode); }
    public static void recordDepthFunc(int func) { if (currentRecorder != null) currentRecorder.recordDepthFunc(func); }
    public static void recordBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) { if (currentRecorder != null) currentRecorder.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha); }
    public static void recordAlphaFunc(int func, float ref) { if (currentRecorder != null) currentRecorder.recordAlphaFunc(func, ref); }
    public static void recordCullFace(int mode) { if (currentRecorder != null) currentRecorder.recordCullFace(mode); }
    public static void recordShadeModel(int mode) { if (currentRecorder != null) currentRecorder.recordShadeModel(mode); }
    public static void recordBindTexture(int target, int texture) { if (currentRecorder != null) currentRecorder.recordBindTexture(target, texture); }
    public static void recordTexParameteri(int target, int pname, int param) { if (currentRecorder != null) currentRecorder.recordTexParameteri(target, pname, param); }
    public static void recordTexParameterf(int target, int pname, float param) { if (currentRecorder != null) currentRecorder.recordTexParameterf(target, pname, param); }
    public static void recordMatrixMode(int mode) { if (currentRecorder != null) currentRecorder.recordMatrixMode(mode); }
    public static void recordLoadIdentity(int matrixMode) { if (currentRecorder != null) currentRecorder.recordLoadIdentity(matrixMode); }
    public static void recordPushMatrix(int matrixMode) { if (currentRecorder != null) currentRecorder.recordPushMatrix(matrixMode); }
    public static void recordPopMatrix(int matrixMode) { if (currentRecorder != null) currentRecorder.recordPopMatrix(matrixMode); }
    public static void recordTranslate(int matrixMode, double x, double y, double z) { if (currentRecorder != null) currentRecorder.recordTranslate(matrixMode, x, y, z); }
    public static void recordRotate(int matrixMode, double angle, double x, double y, double z) { if (currentRecorder != null) currentRecorder.recordRotate(matrixMode, angle, x, y, z); }
    public static void recordScale(int matrixMode, double x, double y, double z) { if (currentRecorder != null) currentRecorder.recordScale(matrixMode, x, y, z); }
    public static void recordMultMatrix(int matrixMode, Matrix4f matrix) { if (currentRecorder != null) currentRecorder.recordMultMatrix(matrixMode, matrix); }
    public static void recordLoadMatrix(int matrixMode, Matrix4f matrix) { if (currentRecorder != null) currentRecorder.recordLoadMatrix(matrixMode, matrix); }
    public static void recordOrtho(double left, double right, double bottom, double top, double zNear, double zFar) { if (currentRecorder != null) currentRecorder.recordOrtho(left, right, bottom, top, zNear, zFar); }
    public static void recordFrustum(double left, double right, double bottom, double top, double zNear, double zFar) { if (currentRecorder != null) currentRecorder.recordFrustum(left, right, bottom, top, zNear, zFar); }
    public static void recordViewport(int x, int y, int width, int height) { if (currentRecorder != null) currentRecorder.recordViewport(x, y, width, height); }
    public static void recordPointSize(float size) { if (currentRecorder != null) currentRecorder.recordPointSize(size); }
    public static void recordLineWidth(float width) { if (currentRecorder != null) currentRecorder.recordLineWidth(width); }
    public static void recordLineStipple(int factor, int pattern) { if (currentRecorder != null) currentRecorder.recordLineStipple(factor, pattern); }
    public static void recordPolygonOffset(float factor, float units) { if (currentRecorder != null) currentRecorder.recordPolygonOffset(factor, units); }
    public static void recordPolygonMode(int face, int mode) { if (currentRecorder != null) currentRecorder.recordPolygonMode(face, mode); }
    public static void recordColorMaterial(int face, int mode) { if (currentRecorder != null) currentRecorder.recordColorMaterial(face, mode); }
    public static void recordLogicOp(int opcode) { if (currentRecorder != null) currentRecorder.recordLogicOp(opcode); }
    public static void recordActiveTexture(int texture) { if (currentRecorder != null) currentRecorder.recordActiveTexture(texture); }
    public static void recordUseProgram(int program) { if (currentRecorder != null) currentRecorder.recordUseProgram(program); }
    public static void recordPushAttrib(int mask) { if (currentRecorder != null) currentRecorder.recordPushAttrib(mask); }
    public static void recordPopAttrib() { if (currentRecorder != null) currentRecorder.recordPopAttrib(); }
    public static void recordFogf(int pname, float param) { if (currentRecorder != null) currentRecorder.recordFogf(pname, param); }
    public static void recordFogi(int pname, int param) { if (currentRecorder != null) currentRecorder.recordFogi(pname, param); }
    public static void recordHint(int target, int mode) { if (currentRecorder != null) currentRecorder.recordHint(target, mode); }
    public static void recordFog(int pname, java.nio.FloatBuffer params) { if (currentRecorder != null) currentRecorder.recordFog(pname, params); }
    public static void recordLightf(int light, int pname, float param) { if (currentRecorder != null) currentRecorder.recordLightf(light, pname, param); }
    public static void recordLighti(int light, int pname, int param) { if (currentRecorder != null) currentRecorder.recordLighti(light, pname, param); }
    public static void recordLight(int light, int pname, java.nio.FloatBuffer params) { if (currentRecorder != null) currentRecorder.recordLight(light, pname, params); }
    public static void recordLightModelf(int pname, float param) { if (currentRecorder != null) currentRecorder.recordLightModelf(pname, param); }
    public static void recordLightModeli(int pname, int param) { if (currentRecorder != null) currentRecorder.recordLightModeli(pname, param); }
    public static void recordLightModel(int pname, java.nio.FloatBuffer params) { if (currentRecorder != null) currentRecorder.recordLightModel(pname, params); }
    public static void recordMaterialf(int face, int pname, float val) { if (currentRecorder != null) currentRecorder.recordMaterialf(face, pname, val); }
    public static void recordMaterial(int face, int pname, java.nio.FloatBuffer params) { if (currentRecorder != null) currentRecorder.recordMaterial(face, pname, params); }
    public static void recordStencilFunc(int func, int ref, int mask) { if (currentRecorder != null) currentRecorder.recordStencilFunc(func, ref, mask); }
    public static void recordStencilFuncSeparate(int face, int func, int ref, int mask) { if (currentRecorder != null) currentRecorder.recordStencilFuncSeparate(face, func, ref, mask); }
    public static void recordStencilOp(int fail, int zfail, int zpass) { if (currentRecorder != null) currentRecorder.recordStencilOp(fail, zfail, zpass); }
    public static void recordStencilOpSeparate(int face, int sfail, int dpfail, int dppass) { if (currentRecorder != null) currentRecorder.recordStencilOpSeparate(face, sfail, dpfail, dppass); }
    public static void recordStencilMask(int mask) { if (currentRecorder != null) currentRecorder.recordStencilMask(mask); }
    public static void recordStencilMaskSeparate(int face, int mask) { if (currentRecorder != null) currentRecorder.recordStencilMaskSeparate(face, mask); }
    public static void recordCallList(int listId) { if (currentRecorder != null) currentRecorder.recordCallList(listId); }
    public static void recordDrawBuffer(int mode) { if (currentRecorder != null) currentRecorder.recordDrawBuffer(mode); }
    public static void recordDrawBuffers(int count, int buf) { if (currentRecorder != null) currentRecorder.recordDrawBuffers(count, buf); }
    public static void recordDrawBuffers(int count, java.nio.IntBuffer bufs) { if (currentRecorder != null) currentRecorder.recordDrawBuffers(count, bufs); }
    public static void recordDrawArrays(int mode, int start, int count) { if (currentRecorder != null) currentRecorder.recordDrawArrays(mode, start, count); }
    public static void recordBindVBO(int vbo) { if (currentRecorder != null) currentRecorder.recordBindVBO(vbo); }
    public static void recordBindVAO(int vao) { if (currentRecorder != null) currentRecorder.recordBindVAO(vao); }

    public static void recordComplexCommand(DisplayListCommand cmd) { if (currentRecorder != null) currentRecorder.recordComplexCommand(cmd); }

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
        // Get relative transform (changes since glNewList, not absolute matrix state)
        final Matrix4f currentTransform = new Matrix4f(relativeTransform);

        if (tessellator.isEmpty()) return;

        final AccumulatedDraw draw = new AccumulatedDraw(tessellator, currentTransform, getCommandCount());
        accumulatedDraws.add(draw);
        tessellator.reset();
    }

    /**
     * Update the relative transform during display list compilation.
     * Called by GLStateManager matrix methods (glTranslate, glRotate, glScale).
     *
     * @param x Translation x, scale x, or rotation angle (degrees)
     * @param y Translation y or scale y (ignored for rotate)
     * @param z Translation z or scale z (ignored for rotate)
     * @param op The operation type
     * @param rotationAxis For rotation only, the axis vector (null otherwise)
     */
    public static void updateRelativeTransform(float x, float y, float z, TransformOp op, Vector3f rotationAxis) {
        if (relativeTransform == null || !GLStateManager.isModelViewMatrix()) {
            return;
        }

        switch (op) {
            case TRANSLATE -> relativeTransform.translate(x, y, z);
            case SCALE -> relativeTransform.scale(x, y, z);
            case ROTATE -> {
                if (rotationAxis != null) {
                    relativeTransform.rotate((float) Math.toRadians(x), rotationAxis);
                }
            }
        }
    }

    /**
     * Update the relative transform with a matrix multiplication during display list compilation.
     * Called by GLStateManager.glMultMatrix().
     *
     * @param matrix The matrix to multiply
     */
    public static void updateRelativeTransform(Matrix4f matrix) {
        if (relativeTransform == null) {
            return;
        }

        // Only track MODELVIEW - projection/texture don't affect baked geometry
        if (!GLStateManager.isModelViewMatrix()) {
            return;
        }

        relativeTransform.mul(matrix);
    }

    /**
     * Push the current MODELVIEW transform onto the stack.
     * Only operates when current matrix mode is MODELVIEW.
     * Called by GLStateManager.glPushMatrix() during recording.
     */
    public static void pushRelativeTransform() {
        if (relativeTransform == null) {
            return;
        }

        // Only track MODELVIEW - projection/texture don't affect baked geometry
        if (!GLStateManager.isModelViewMatrix()) {
            return;
        }

        relativeTransform.pushMatrix();
    }

    /**
     * Pop the MODELVIEW transform from stack.
     * Only operates when current matrix mode is MODELVIEW.
     * Called by GLStateManager.glPopMatrix() during recording.
     */
    public static void popRelativeTransform() {
        if (relativeTransform == null) {
            return;
        }

        // Only track MODELVIEW - projection/texture don't affect baked geometry
        if (!GLStateManager.isModelViewMatrix()) {
            return;
        }

        try {
            relativeTransform.popMatrix();
        } catch (RuntimeException e) {
            LOGGER.warn("[DisplayList] Transform stack underflow in list={}", glListId);
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
     * Used by CallListCmd to execute nested display lists with the unoptimized version.
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
                glListId, glListMode, currentRecorder, accumulatedDraws, relativeTransform, immediateModeRecorder
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
        relativeTransform.identity();  // Track relative transforms from identity
        immediateModeRecorder = new ImmediateModeRecorder();  // For glBegin/glEnd/glVertex

        TessellatorManager.startCapturingDirect(new DirectTessellator((tessellator) -> {
            if (tessellator.isEmpty()) return true;
            final Matrix4f currentTransform = new Matrix4f(relativeTransform);
            final int cmdIndex = getCommandCount();
            accumulatedDraws.add(new AccumulatedDraw(tessellator, currentTransform, cmdIndex));
            tessellator.reset();
            return true;
        }));

        // We hijack display list compilation completely - no GL11.glNewList() calls
        // During compile don't actually apply any changes to GL, but do track them
        GLStateManager.getModelViewMatrix().pushMatrix();  // Save current model view matrix state
        GLStateManager.pushState(GL11.GL_ALL_ATTRIB_BITS);
    }

    /**
     * End display list compilation and build optimized/unoptimized versions.
     */
    public static void glEndList() {
        if (glListMode == 0) {
            throw new RuntimeException("glEndList called outside of a display list!");
        }
        final boolean needsRender = glListMode == GL11.GL_COMPILE_AND_EXECUTE;
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
            displayListCache.put(glListId, compiled);
        } else {
            // Free the recorder even if empty
            if (currentRecorder != null) {
                currentRecorder.free();
            }
            compiled = null;
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

            // Note: TessellatorManager callback stack was popped by stopCompiling()
            // Parent's COMPILING callback is now active again
        } else {
            // Not nested - clear all compilation state
            currentRecorder = null;
            recordingThread = null;
            accumulatedDraws = null;
            relativeTransform = null;
            immediateModeRecorder = null;
            glListId = -1;
            glListMode = 0;
        }

        GLStateManager.getModelViewMatrix().popMatrix();
        GLStateManager.popState();

        double diff = System.nanoTime() - time;
        System.out.println("Display list compilation took " + (diff / 1_000_000d) + "ms.");
        System.out.println(new Exception().getStackTrace()[2].toString());

        // Only execute if COMPILE_AND_EXECUTE AND this is the root (non-nested) list
        // Nested lists will execute naturally when the parent executes via CallListCmd
        if (needsRender && !isNested && compiled != null) {
            compiled.render();
        }
    }

    /**
     * Execute a compiled display list.
     */
    public static void glCallList(int list) {
        // If we're currently recording, record a reference to this list Per OpenGL spec: the inner list is NOT executed during compilation.
        // It only executes when the parent list is played back.
        // For GL_COMPILE_AND_EXECUTE: the entire parent list (including this CallListCmd) executes at the end of glEndList, not during compilation.
        if (currentRecorder != null) {
            // Record a CallList command that will execute the referenced list during playback
            recordCallList(list);
            return;
        }

        // Normal playback mode
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
            builder.addDraw(draw.format, draw.drawMode, draw.drawData);
        }
        return builder.build();
    }

    public static ByteBuffer merge(List<ByteBuffer> buffers) {
        int totalSize = 0;

        for (ByteBuffer buffer : buffers) {
            totalSize += buffer.remaining();
        }

        ByteBuffer merged = BufferUtils.createByteBuffer(totalSize);

        for (ByteBuffer buffer : buffers) {
            // duplicate so we don't modify position/limit of the original
            merged.put(buffer.duplicate());
        }

        merged.flip();
        return merged;
    }

}
