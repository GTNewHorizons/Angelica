package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.CallListCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawVBOCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.OptimizationContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;


import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
 * <p>The {@link DrawBatcher} merges consecutive draws that share the same vertex flags into
 * single VBOs. Transforms are handled separately via {@link TransformOptimizer}.</p>
 *
 * @see com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList
 * @see com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw
 * @see com.gtnewhorizons.angelica.glsm.recording.commands.DrawVBOCmd
 */
@UtilityClass
public class DisplayListManager {
    private static final Logger LOGGER = LogManager.getLogger("DisplayListManager");

    private static boolean isIdentity(Matrix4f m) {
        return (m.properties() & Matrix4f.PROPERTY_IDENTITY) != 0;
    }

    /** Transform operation types for relative transform tracking. */
    public enum TransformOp { TRANSLATE, SCALE, ROTATE }

    // Display list compilation state (current/active context)
    private static int glListMode = 0;
    private static int glListId = -1;
    private static boolean tessellatorCompiling = false;  // Track if we started compiling
    private static List<DisplayListCommand> currentCommands = null;  // null when not recording
    private static volatile Thread recordingThread = null;  // Thread that started recording (for thread-safety)
    private static List<AccumulatedDraw> accumulatedDraws = null;  // Accumulates draws for batching
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
        List<DisplayListCommand> commands,
        List<AccumulatedDraw> draws,
        Matrix4fStack transform,
        boolean wasTessellatorCompiling,
        ImmediateModeRecorder immediateRecorder
    ) {}

    /**
     * Check if the current thread is recording a display list.
     * Only returns true for the thread that started recording.
     * This prevents other threads from having their GL calls captured.
     */
    public static boolean isRecording() {
        return currentCommands != null && Thread.currentThread() == recordingThread;
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
     * Record a command during display list compilation.
     * If not recording, this is a no-op.
     *
     * @param cmd The command to record
     */
    public static void recordCommand(DisplayListCommand cmd) {
        if (currentCommands != null) {
            currentCommands.add(cmd);
        }
    }

    /**
     * Add an immediate mode draw to the current display list compilation.
     * Called by GLStateManager.glEnd() when immediate mode geometry is ready.
     *
     * <p>This method captures the current transform and command position, creating
     * an AccumulatedDraw that will be interleaved correctly with other commands
     * during display list playback.
     *
     * @param result The immediate mode result containing quads and flags
     */
    public static void addImmediateModeDraw(ImmediateModeRecorder.Result result) {
        if (accumulatedDraws == null || result == null || result.quads().isEmpty()) {
            return;
        }

        // Get relative transform (changes since glNewList, not absolute matrix state)
        final Matrix4f currentTransform = new Matrix4f(relativeTransform);

        // Create AccumulatedDraw at current command position
        final AccumulatedDraw draw = new AccumulatedDraw(result.quads(), currentTransform, result.flags(), currentCommands != null ? currentCommands.size() : 0);
        accumulatedDraws.add(draw);
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

    /**
     * Start display list compilation.
     * Supports nested glNewList() calls (spec-violating but needed for mod compatibility).
     */
    public static void glNewList(int list, int mode) {
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
                glListId, glListMode, currentCommands, accumulatedDraws, relativeTransform, tessellatorCompiling,
                immediateModeRecorder
            );
            compilationStack.push(parentContext);
        }

        // Initialize fresh context for this (possibly nested) list
        glListId = list;
        glListMode = mode;
        recordingThread = Thread.currentThread();  // Track which thread is recording
        currentCommands = new ArrayList<>(256);   // Typical display list command count
        accumulatedDraws = new ArrayList<>(64);   // Fewer draws than commands typically
        relativeTransform = new Matrix4fStack(GLStateManager.MAX_MODELVIEW_STACK_DEPTH);
        relativeTransform.identity();  // Track relative transforms from identity
        immediateModeRecorder = new ImmediateModeRecorder();  // For glBegin/glEnd/glVertex

        // Start compiling mode with per-draw callback (works for both root and nested lists now)
        // Capture listId in final variable for lambda closure
        final int capturedListId = list;
        TessellatorManager.setCompiling((quads, flags) -> {
            if (quads.isEmpty()) {
                LOGGER.warn("[VBO Display List] Empty draw call in list={}", capturedListId);
                return;
            }

            // Get relative transform (changes since glNewList, not absolute matrix state)
            final Matrix4f currentTransform = new Matrix4f(relativeTransform);

            // Accumulate this draw for batching at glEndList()
            final AccumulatedDraw draw = new AccumulatedDraw(quads, currentTransform, flags, currentCommands.size());
            accumulatedDraws.add(draw);
        });
        tessellatorCompiling = true;

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
        if (tessellatorCompiling) {
            TessellatorManager.stopCompiling();
            tessellatorCompiling = false;
        }

        final CompiledDisplayList compiled;
        // Create CompiledDisplayList with both unoptimized and optimized versions
        final boolean hasCommands = currentCommands != null && !currentCommands.isEmpty();
        final boolean hasDraws = accumulatedDraws != null && !accumulatedDraws.isEmpty();

        if (hasCommands || hasDraws) {
            // Build both versions using extracted functions
            final List<DisplayListCommand> unoptimized = buildUnoptimizedDisplayList(currentCommands != null ? currentCommands : new ArrayList<>(), accumulatedDraws, glListId);
            final List<DisplayListCommand> optimized = buildOptimizedDisplayList(currentCommands != null ? currentCommands : new ArrayList<>(), accumulatedDraws, glListId);

            compiled = new CompiledDisplayList(optimized, unoptimized);
            displayListCache.put(glListId, compiled);
        } else {
            compiled = null;
        }

        // Handle nested compilation - restore parent context
        if (isNested) {
            final CompilationContext parentContext = compilationStack.pop();

            // Restore parent context
            glListId = parentContext.listId;
            glListMode = parentContext.listMode;
            currentCommands = parentContext.commands;
            accumulatedDraws = parentContext.draws;
            relativeTransform = parentContext.transform;
            tessellatorCompiling = parentContext.wasTessellatorCompiling;
            immediateModeRecorder = parentContext.immediateRecorder;

            // Note: TessellatorManager callback stack was popped by stopCompiling()
            // Parent's COMPILING callback is now active again
        } else {
            // Not nested - clear all compilation state
            currentCommands = null;
            recordingThread = null;
            accumulatedDraws = null;
            relativeTransform = null;
            immediateModeRecorder = null;
            glListId = -1;
            glListMode = 0;
        }

        GLStateManager.getModelViewMatrix().popMatrix();
        GLStateManager.popState();

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
        if (currentCommands != null) {
            // Record a CallListCmd that will execute the referenced list during playback
            currentCommands.add(new CallListCmd(list));
            return;
        }

        // Normal playback mode
        final CompiledDisplayList compiled = displayListCache.get(list);
        if (compiled != null) {
            GLStateManager.trySyncProgram();
            compiled.render();
            return;
        }

        // Fall back to VBOManager or legacy display list
        if (list < 0) {
            // Negative IDs are VBOManager space (entity models, etc.)
            final VertexBuffer vbo = VBOManager.get(list);
            if (vbo != null) {
                vbo.render();
            } else {
                LOGGER.warn("[VBO Playback] VBO not found for list={} (was it deleted or never created?)", list);
            }
            // Per OpenGL spec: if list is undefined, glCallList has no effect
            // Entity models use lazy compilation, so list may not exist yet
        } else {
            // Positive IDs may be real GL display lists
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

    /**
     * Build unoptimized display list: keeps all commands including matrix transforms.
     * For nested display list calls where matrices need to compose with parent state.
     */
    private static List<DisplayListCommand> buildUnoptimizedDisplayList(List<DisplayListCommand> currentCommands, List<AccumulatedDraw> accumulatedDraws, int glListId) {

        if (accumulatedDraws == null || accumulatedDraws.isEmpty()) {
            return new ArrayList<>(currentCommands);
        }

        final List<DisplayListCommand> unoptimized = new ArrayList<>(currentCommands.size() + accumulatedDraws.size());
        int drawIndex = 0;

        // Insert draws at their recorded positions in the command stream
        for (int i = 0; i < currentCommands.size(); i++) {
            // Insert any draws that belong at this position
            while (drawIndex < accumulatedDraws.size() && accumulatedDraws.get(drawIndex).commandIndex == i) {
                final AccumulatedDraw draw = accumulatedDraws.get(drawIndex);

                // Compile untransformed quads - matrix commands in the list will apply transforms via GL stack
                final VertexBuffer vbo = compileQuads(draw.quads, draw.flags);
                unoptimized.add(new DrawVBOCmd(vbo, draw.flags.hasBrightness));

                drawIndex++;
            }

            // Add the original command (including matrix commands for nested list composition)
            unoptimized.add(currentCommands.get(i));
        }

        // Insert any remaining draws at the end
        while (drawIndex < accumulatedDraws.size()) {
            final AccumulatedDraw draw = accumulatedDraws.get(drawIndex);
            final VertexBuffer vbo = compileQuads(draw.quads, draw.flags);
            unoptimized.add(new DrawVBOCmd(vbo, draw.flags.hasBrightness));
            drawIndex++;
        }

        return unoptimized;
    }

    /**
     * Build optimized display list: batches draws with same flags, collapses MODELVIEW transforms.
     * Instead of baking transforms into vertices, we emit collapsed MultMatrix commands at barriers.
     * This properly handles nested display lists (CallList) which need GL state to be correct.
     *
     * <p>Transform collapsing strategy:
     * <ul>
     *   <li>Track accumulated MODELVIEW transform during command stream analysis</li>
     *   <li>At barriers (Draw, CallList), emit a single MultMatrix if transform changed</li>
     *   <li>Push/Pop maintain proper stack semantics</li>
     *   <li>Vertices stay canonical (untransformed) in VBOs</li>
     * </ul>
     */
    // Package-private for testing
    static List<DisplayListCommand> buildOptimizedDisplayList(List<DisplayListCommand> currentCommands, List<AccumulatedDraw> accumulatedDraws, int glListId) {
        final boolean hasDraws = accumulatedDraws != null && !accumulatedDraws.isEmpty();
        final List<DisplayListCommand> optimized = new ArrayList<>();
        final DrawBatcher batcher = new DrawBatcher(glListId);
        final TransformOptimizer transformOpt = new TransformOptimizer(glListId);
        final OptimizationContextImpl ctx = new OptimizationContextImpl(transformOpt, batcher, optimized);

        int drawIndex = 0;
        for (int i = 0; i < currentCommands.size(); i++) {
            // Process draws at this command position
            while (hasDraws && drawIndex < accumulatedDraws.size() && accumulatedDraws.get(drawIndex).commandIndex == i) {
                processDraw(accumulatedDraws.get(drawIndex++), ctx);
            }
            final DisplayListCommand cmd = currentCommands.get(i);
            if (cmd.handleOptimization(ctx)) {
                optimized.add(cmd);
            }
        }

        // Process remaining draws at end of command stream
        while (hasDraws && drawIndex < accumulatedDraws.size()) {
            processDraw(accumulatedDraws.get(drawIndex++), ctx);
        }

        // Flush final batch
        if (hasDraws) {
            batcher.flush(optimized);
        }

        // Emit residual transform to match expected GL state
        if (!transformOpt.isIdentity()) {
            transformOpt.emitPendingTransform(optimized);
        }

        return optimized;
    }

    /**
     * Process a single draw during optimization.
     * Handles transform synchronization and batching.
     */
    private static void processDraw(AccumulatedDraw draw, OptimizationContextImpl ctx) {
        // Emit transform if this draw expects different state than what we've emitted
        if (ctx.transformOpt.needsTransformForDraw(draw.transform)) {
            ctx.batcher.flush(ctx.output);
            ctx.transformOpt.emitTransformTo(ctx.output, draw.transform);
        }

        // Batch or flush-and-start-new-batch
        if (ctx.batcher.canBatch(draw)) {
            ctx.batcher.addToBatch(draw);
        } else {
            ctx.batcher.flush(ctx.output);
            ctx.batcher.addToBatch(draw);
        }
    }

    /**
     * Implementation of OptimizationContext that wraps transform optimizer and batcher.
     */
    @Desugar
    private record OptimizationContextImpl(
        TransformOptimizer transformOpt,
        DrawBatcher batcher,
        List<DisplayListCommand> output
    ) implements OptimizationContext {
        @Override public Matrix4f getAccumulatedTransform() { return transformOpt.getAccumulated(); }
        @Override public void loadIdentity() { transformOpt.loadIdentity(); }
        @Override public void pushTransform() { transformOpt.pushTransform(); }
        @Override public void popTransform() { transformOpt.popTransform(); }
        @Override public void emitPendingTransform() { transformOpt.emitPendingTransform(output); }
        @Override public void emitTransformTo(Matrix4f target) { transformOpt.emitTransformTo(output, target); }
        @Override public void flushBatcher() { batcher.flush(output); }
        @Override public Matrix4f getBatchTransform() { return batcher.getBatchTransform(); }
        @Override public void emit(DisplayListCommand cmd) { output.add(cmd); }
    }

    /**
     * Helper class for tracking and collapsing MODELVIEW transforms during optimization.
     * Implements the transform accumulation and emission strategy:
     * - accumulated: total relative transform (what GL matrix should be)
     * - lastEmitted: what we've emitted (what GL matrix is)
     * - stack: saved accumulated values at Push points
     */
    private static class TransformOptimizer {
        private final Matrix4f accumulated = new Matrix4f();  // Current accumulated transform
        private final Matrix4f lastEmitted = new Matrix4f();  // Last emitted transform
        private final Deque<Matrix4f> stack = new ArrayDeque<>();  // For Push/Pop
        private final int listId;  // For logging

        TransformOptimizer(int listId) {
            this.listId = listId;
            accumulated.identity();
            lastEmitted.identity();
        }

        Matrix4f getAccumulated() {
            return accumulated;
        }

        void loadIdentity() {
            accumulated.identity();
        }

        void pushTransform() {
            stack.push(new Matrix4f(accumulated));
        }

        void popTransform() {
            if (!stack.isEmpty()) {
                final Matrix4f popped = stack.pop();
                accumulated.set(popped);
                // After Pop, GL state is restored to the pushed value so lastEmitted should also reflect this
                lastEmitted.set(accumulated);
            } else {
                LOGGER.warn("[TransformOptimizer] list={} Pop with empty stack - resetting to identity", listId);
                accumulated.identity();
                lastEmitted.identity();
            }
        }

        boolean isIdentity() {
            return DisplayListManager.isIdentity(accumulated);
        }

        /**
         * Check if there's a pending transform that hasn't been emitted yet.
         */
        boolean hasPendingTransform() {
            return !accumulated.equals(lastEmitted);
        }

        /**
         * Emit a MultMatrix command if accumulated differs from lastEmitted.
         * Updates lastEmitted to match accumulated after emission.
         */
        void emitPendingTransform(List<DisplayListCommand> output) {
            if (!accumulated.equals(lastEmitted)) {
                emitTransformTo(output, accumulated);
            }
        }

        /**
         * Emit a MultMatrix command to make GL state match a target transform.
         * Used when a draw expects a specific transform that may differ from accumulated.
         * Updates lastEmitted to match target after emission.
         *
         * @param output The command list to emit to
         * @param target The target transform to reach
         */
        void emitTransformTo(List<DisplayListCommand> output, Matrix4f target) {
            if (target.equals(lastEmitted)) {
                // Already at target - nothing to emit
                return;
            }

            // Calculate delta: what we need to multiply to go from lastEmitted to target
            // delta = inverse(lastEmitted) * target
            // But for simplicity, if lastEmitted is identity, just emit target
            if (DisplayListManager.isIdentity(lastEmitted)) {
                // Simple case: just emit target
                output.add(MultMatrixCmd.create(target, GL11.GL_MODELVIEW));
            } else {
                // Need to emit delta: inv(lastEmitted) * target
                final Matrix4f delta = new Matrix4f(lastEmitted).invert().mul(target);
                output.add(MultMatrixCmd.create(delta, GL11.GL_MODELVIEW));
            }
            lastEmitted.set(target);
        }

        /**
         * Check if a draw's expected transform differs from what we've emitted.
         */
        boolean needsTransformForDraw(Matrix4f drawTransform) {
            return !drawTransform.equals(lastEmitted);
        }

    }

    /**
     * Helper class for batching draws during optimized display list building.
     * Batches consecutive draws with the same vertex flags AND same transform into a single VBO.
     * Transforms are NOT baked - they're handled via collapsed MultMatrix commands.
     *
     * IMPORTANT: Draws can only be batched if they expect the SAME transform state.
     * The draw.transform field records what GL matrix state was active when the draw was captured.
     * Batching draws with different transforms would render them incorrectly.
     */
    private static class DrawBatcher {
        List<ModelQuadViewMutable> currentBatch = null;
        CapturingTessellator.Flags batchFlags = null;
        Matrix4f batchTransform = null;  // Transform that draws in this batch expect
        int batchDrawCount = 0;
        final int listId;

        DrawBatcher(int listId) {
            this.listId = listId;
        }

        void flush(List<DisplayListCommand> outputList) {
            if (currentBatch != null && !currentBatch.isEmpty()) {
                // Compile without transform - vertices stay canonical
                final VertexBuffer vbo = compileQuads(currentBatch, batchFlags);
                outputList.add(new DrawVBOCmd(vbo, batchFlags.hasBrightness));

                currentBatch = null;
                batchFlags = null;
                batchTransform = null;
                batchDrawCount = 0;
            }
        }

        /**
         * Check if a draw can be batched with the current batch.
         * Requires BOTH: same vertex flags AND same expected transform.
         * Batching draws with different transforms would render incorrectly!
         */
        boolean canBatch(AccumulatedDraw draw) {
            if (currentBatch == null) {
                return false;
            }
            if (!batchFlags.equals(draw.flags)) {
                return false;
            }
            // Draws captured at different transform states cannot be batched together.
            return batchTransform.equals(draw.transform);
        }

        /**
         * Get the transform that the current batch expects, or null if no batch.
         */
        Matrix4f getBatchTransform() {
            return batchTransform;
        }

        void addToBatch(AccumulatedDraw draw) {
            if (currentBatch == null) {
                currentBatch = new ArrayList<>(draw.quads);
                batchFlags = draw.flags;
                batchTransform = new Matrix4f(draw.transform);  // Copy - draws may share matrix instances
                batchDrawCount = 1;
            } else {
                currentBatch.addAll(draw.quads);
                batchDrawCount++;
            }
        }
    }

    /**
     * Selects the optimal VertexFormat from GTNHLib defaults based on actual attribute usage.
     * This reduces memory usage by excluding unused attributes.
     */
    private static VertexFormat selectOptimalFormat(CapturingTessellator.Flags flags) {
        final boolean hasColor = flags.hasColor;
        final boolean hasTexture = flags.hasTexture;
        final boolean hasBrightness = flags.hasBrightness;
        final boolean hasNormals = flags.hasNormals;

        final VertexFormat format;

        // Map flags to GTNHLib formats, ordered by guess at frequency/likelihood
        if (!hasColor && hasTexture && !hasBrightness && !hasNormals) {
            format = DefaultVertexFormat.POSITION_TEXTURE;  // Entity models, common
        } else if (!hasColor && !hasTexture && !hasBrightness && !hasNormals) {
            format = DefaultVertexFormat.POSITION;  // Sky rendering, simple geometry
        } else if (hasColor && hasTexture && !hasBrightness && !hasNormals) {
            format = DefaultVertexFormat.POSITION_COLOR_TEXTURE;  // Colored blocks/UI
        } else if (!hasColor && hasTexture && hasBrightness && hasNormals) {
            format = DefaultVertexFormat.POSITION_TEXTURE_LIGHT_NORMAL;  // BuildCraft fluids
        } else if (!hasColor && hasTexture && !hasBrightness && hasNormals) {
            format = DefaultVertexFormat.POSITION_TEXTURE_NORMAL;  // Lit geometry with normals
        } else if (hasColor && hasTexture && hasBrightness && !hasNormals) {
            format = DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;  // Colored lit blocks
        } else if (hasColor && !hasTexture && !hasBrightness && !hasNormals) {
            format = DefaultVertexFormat.POSITION_COLOR;  // Colored lines/particles
        } else {
            format = DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;  // Full format fallback
        }

        return format;
    }

    /**
     * Compile captured Tessellator quads into a VertexBuffer (VAO or VBO).
     * Uses VertexBuffer for proper compatibility with fixed-function pipeline.
     *
     * @param quads The quads to compile
     * @param flags The vertex attribute flags
     * @return The uploaded VertexBuffer
     */
    private static VertexBuffer compileQuads(List<ModelQuadViewMutable> quads, CapturingTessellator.Flags flags) {
        // Select optimal predefined format from GTNHLib
        final VertexFormat format = selectOptimalFormat(flags);

        // Use VAOManager to create VAO with optimal format (falls back to VBO if unsupported)
        final VertexBuffer vao = VAOManager.createVAO(format, GL11.GL_QUADS);

        final ByteBuffer buffer = CapturingTessellator.quadsToBuffer(quads, format);

        vao.upload(buffer);

        return vao;
    }
}
