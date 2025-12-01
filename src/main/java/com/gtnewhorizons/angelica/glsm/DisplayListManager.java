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
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedLineDraw;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.CallListCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawRangeCmd;
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
import java.util.Collections;
import java.util.Comparator;
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

    static boolean isIdentity(Matrix4f m) {
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
    private static List<AccumulatedDraw> accumulatedDraws = null;  // Accumulates quad draws for batching
    private static List<AccumulatedLineDraw> accumulatedLineDraws = null;  // Accumulates line draws
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
        List<AccumulatedLineDraw> lineDraws,
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
     * @param result The immediate mode result containing quads, lines, and flags
     */
    public static void addImmediateModeDraw(ImmediateModeRecorder.Result result) {
        if (accumulatedDraws == null || result == null) {
            return;
        }

        // Get relative transform (changes since glNewList, not absolute matrix state)
        final Matrix4f currentTransform = new Matrix4f(relativeTransform);
        final int commandPosition = currentCommands != null ? currentCommands.size() : 0;

        // Add quad draw if we have quads
        if (!result.quads().isEmpty()) {
            final AccumulatedDraw quadDraw = new AccumulatedDraw(result.quads(), currentTransform, result.flags(), commandPosition);
            accumulatedDraws.add(quadDraw);
        }

        // Add line draw if we have lines
        if (!result.lines().isEmpty()) {
            final AccumulatedLineDraw lineDraw = new AccumulatedLineDraw(result.lines(), currentTransform, result.flags(), commandPosition);
            accumulatedLineDraws.add(lineDraw);
        }
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
                glListId, glListMode, currentCommands, accumulatedDraws, accumulatedLineDraws,
                relativeTransform, tessellatorCompiling, immediateModeRecorder
            );
            compilationStack.push(parentContext);
        }

        // Initialize fresh context for this (possibly nested) list
        glListId = list;
        glListMode = mode;
        recordingThread = Thread.currentThread();  // Track which thread is recording
        currentCommands = new ArrayList<>(256);   // Typical display list command count
        accumulatedDraws = new ArrayList<>(64);   // Fewer draws than commands typically
        accumulatedLineDraws = new ArrayList<>(16);  // Line draws are less common
        relativeTransform = new Matrix4fStack(GLStateManager.MAX_MODELVIEW_STACK_DEPTH);
        relativeTransform.identity();  // Track relative transforms from identity
        immediateModeRecorder = new ImmediateModeRecorder();  // For glBegin/glEnd/glVertex

        // Start compiling mode with per-draw callback (works for both root and nested lists now)
        TessellatorManager.setCompiling((quads, flags) -> {
            if (quads.isEmpty()) {
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
        final boolean hasLineDraws = accumulatedLineDraws != null && !accumulatedLineDraws.isEmpty();

        if (hasCommands || hasDraws || hasLineDraws) {
            // Phase 1: Compile format-based VBOs (shared by both optimized and unoptimized paths)
            final Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers = compileFormatBasedVBOs(accumulatedDraws);

            // Phase 2: Compile line VBO (shared by both paths)
            final CompiledLineBuffer compiledLineBuffer = compileLineBuffer(accumulatedLineDraws);

            // Collect all owned VBOs (quads + lines)
            final VertexBuffer[] ownedVbos = extractOwnedVbos(compiledQuadBuffers, compiledLineBuffer);

            final List<DisplayListCommand> cmds = currentCommands != null ? currentCommands : new ArrayList<>();

            /* Phase 3: Build optimized commands (collapsed transforms, merged ranges) */
            final DisplayListCommand[] optimized = buildOptimizedCommands(cmds, compiledQuadBuffers, compiledLineBuffer, glListId);

            // Phase 4: Build unoptimized commands (original transforms, per-draw ranges)
            // Only built when debug flag is enabled - saves CPU and memory in production
            final DisplayListCommand[] unoptimized = DEBUG_DISPLAY_LISTS
                ? buildUnoptimizedCommands(cmds, accumulatedDraws, compiledQuadBuffers, compiledLineBuffer)
                : new DisplayListCommand[0];

            compiled = new CompiledDisplayList(optimized, unoptimized, ownedVbos);
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
            accumulatedLineDraws = parentContext.lineDraws;
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
            accumulatedLineDraws = null;
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

    /**
     * Build unoptimized command list using shared VBOs via DrawRangeCmd.
     * Preserves original matrix commands for nested display list composition.
     * Uses perDrawRanges from compiled buffers (1:1 with input draws).
     *
     * @param currentCommands Original matrix/state commands
     * @param accumulatedDraws The accumulated draws (for ordering info)
     * @param compiledQuadBuffers Pre-compiled format-based quad VBOs with perDrawRanges
     * @param compiledLineBuffer Pre-compiled line VBO (may be null)
     */
    private static DisplayListCommand[] buildUnoptimizedCommands(
            List<DisplayListCommand> currentCommands,
            List<AccumulatedDraw> accumulatedDraws,
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer) {

        final boolean hasQuads = accumulatedDraws != null && !accumulatedDraws.isEmpty();
        final boolean hasLines = compiledLineBuffer != null;

        if (!hasQuads && !hasLines && (currentCommands == null || currentCommands.isEmpty())) {
            return new DisplayListCommand[0];
        }

        // Build per-format draw counters to index into perDrawRanges
        final Map<CapturingTessellator.Flags, int[]> perFormatDrawIndex = new HashMap<>();
        for (var key : compiledQuadBuffers.keySet()) {
            perFormatDrawIndex.put(key, new int[]{0});  // Mutable int holder
        }

        final List<DisplayListCommand> unoptimized = new ArrayList<>();
        int drawIndex = 0;
        int lineRangeIndex = 0;
        final DrawRange[] lineRanges = hasLines ? compiledLineBuffer.perDrawRanges() : null;

        // Insert draws at their recorded positions in the command stream
        final int cmdCount = currentCommands != null ? currentCommands.size() : 0;
        for (int i = 0; i < cmdCount; i++) {
            // Insert any quad draws that belong at this position
            while (hasQuads && drawIndex < accumulatedDraws.size() && accumulatedDraws.get(drawIndex).commandIndex == i) {
                final AccumulatedDraw draw = accumulatedDraws.get(drawIndex);
                emitUnoptimizedQuadDraw(draw, compiledQuadBuffers, perFormatDrawIndex, unoptimized);
                drawIndex++;
            }

            // Insert any line draws that belong at this position (using shared line VBO)
            while (hasLines && lineRangeIndex < lineRanges.length && lineRanges[lineRangeIndex].commandIndex() == i) {
                final DrawRange range = lineRanges[lineRangeIndex++];
                unoptimized.add(new DrawRangeCmd(compiledLineBuffer.vbo(), range.startVertex(), range.vertexCount(), false));
            }

            // Add the original command (including matrix commands for nested list composition)
            unoptimized.add(currentCommands.get(i));
        }

        // Insert any remaining quad draws at the end
        while (hasQuads && drawIndex < accumulatedDraws.size()) {
            final AccumulatedDraw draw = accumulatedDraws.get(drawIndex);
            emitUnoptimizedQuadDraw(draw, compiledQuadBuffers, perFormatDrawIndex, unoptimized);
            drawIndex++;
        }

        // Insert any remaining line draws at the end
        while (hasLines && lineRangeIndex < lineRanges.length) {
            final DrawRange range = lineRanges[lineRangeIndex++];
            unoptimized.add(new DrawRangeCmd(compiledLineBuffer.vbo(), range.startVertex(), range.vertexCount(), false));
        }

        return unoptimized.toArray(new DisplayListCommand[0]);
    }

    /**
     * Emit a DrawRangeCmd for a quad draw in the unoptimized path.
     * Uses perDrawRanges from the pre-compiled buffer for this format.
     */
    private static void emitUnoptimizedQuadDraw(
            AccumulatedDraw draw,
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledBuffers,
            Map<CapturingTessellator.Flags, int[]> perFormatDrawIndex,
            List<DisplayListCommand> output) {

        final CompiledFormatBuffer buffer = compiledBuffers.get(draw.flags);
        final int[] indexHolder = perFormatDrawIndex.get(draw.flags);
        final DrawRange range = buffer.perDrawRanges()[indexHolder[0]++];

        output.add(new DrawRangeCmd(
            buffer.vbo(),
            range.startVertex(),
            range.vertexCount(),
            buffer.flags().hasBrightness
        ));
    }

    /**
     * Compile format-based VBOs from accumulated draws.
     * Groups draws by vertex format and compiles each format's geometry into a single VBO.
     * Both optimized and unoptimized paths share these VBOs.
     *
     * @param accumulatedDraws The accumulated draws to compile
     * @return Map from format flags to compiled buffers (VBO + ranges)
     */
    private static Map<CapturingTessellator.Flags, CompiledFormatBuffer> compileFormatBasedVBOs(
            List<AccumulatedDraw> accumulatedDraws) {
        if (accumulatedDraws == null || accumulatedDraws.isEmpty()) {
            return Collections.emptyMap();
        }

        // Group draws by vertex format
        final Map<CapturingTessellator.Flags, FormatBuffer> formatBuffers = new HashMap<>();
        for (AccumulatedDraw draw : accumulatedDraws) {
            formatBuffers.computeIfAbsent(draw.flags, FormatBuffer::new).addDraw(draw);
        }

        // Compile each format's geometry into a single VBO
        final Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiled = new HashMap<>();
        for (var entry : formatBuffers.entrySet()) {
            compiled.put(entry.getKey(), entry.getValue().finish());
        }
        return compiled;
    }

    /**
     * Compile line draws into a shared VBO.
     *
     * @param lineDraws The accumulated line draws
     * @return Compiled line buffer, or null if no lines
     */
    private static CompiledLineBuffer compileLineBuffer(List<AccumulatedLineDraw> lineDraws) {
        if (lineDraws == null || lineDraws.isEmpty()) {
            return null;
        }

        final LineBuffer buffer = new LineBuffer();
        for (AccumulatedLineDraw draw : lineDraws) {
            buffer.addDraw(draw);
        }
        return buffer.finish();
    }

    /**
     * Extract owned VBOs from compiled buffers (quads + lines).
     * These VBOs are shared by both optimized and unoptimized paths.
     *
     * @param compiledQuadBuffers The compiled quad format buffers
     * @param compiledLineBuffer The compiled line buffer (may be null)
     * @return Array of VBOs that need to be deleted when the display list is deleted
     */
    private static VertexBuffer[] extractOwnedVbos(
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer) {
        final List<VertexBuffer> vbos = new ArrayList<>();

        // Add quad VBOs
        for (CompiledFormatBuffer buffer : compiledQuadBuffers.values()) {
            vbos.add(buffer.vbo());
        }

        // Add line VBO if present
        if (compiledLineBuffer != null) {
            vbos.add(compiledLineBuffer.vbo());
        }

        return vbos.toArray(new VertexBuffer[0]);
    }

    /**
     * Build optimized command list using pre-compiled VBOs.
     * Uses mergedRanges (consecutive same-transform draws combined) and collapses MODELVIEW transforms.
     *
     * @param currentCommands Original matrix/state commands
     * @param compiledQuadBuffers Pre-compiled format-based quad VBOs
     * @param compiledLineBuffer Pre-compiled line VBO (may be null)
     * @param glListId Display list ID for logging
     * @return Optimized command array
     */
    private static DisplayListCommand[] buildOptimizedCommands(
            List<DisplayListCommand> currentCommands,
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            int glListId) {

        final List<DisplayListCommand> optimized = new ArrayList<>();
        final TransformOptimizer transformOpt = new TransformOptimizer(glListId);

        // Collect all merged draw ranges (quads + lines) sorted by command index
        final List<DrawRangeWithBuffer> allRanges = new ArrayList<>();

        // Add quad ranges
        for (CompiledFormatBuffer buffer : compiledQuadBuffers.values()) {
            for (DrawRange range : buffer.mergedRanges()) {
                allRanges.add(new DrawRangeWithBuffer(range, buffer.vbo(), buffer.flags().hasBrightness));
            }
        }

        // Add line ranges
        if (compiledLineBuffer != null) {
            for (DrawRange range : compiledLineBuffer.mergedRanges()) {
                allRanges.add(new DrawRangeWithBuffer(range, compiledLineBuffer.vbo(), false));
            }
        }

        allRanges.sort(Comparator.comparingInt(r -> r.range().commandIndex()));

        // Process command stream with interleaved draws
        final OptimizationContextImpl ctx = new OptimizationContextImpl(transformOpt, optimized);

        int rangeIndex = 0;
        for (int i = 0; i < currentCommands.size(); i++) {
            // Emit draw ranges (quads and lines) at this command position
            while (rangeIndex < allRanges.size() && allRanges.get(rangeIndex).range().commandIndex() == i) {
                emitDrawRange(allRanges.get(rangeIndex++), transformOpt, optimized);
            }

            // Process the original command
            final DisplayListCommand cmd = currentCommands.get(i);
            if (cmd.handleOptimization(ctx)) {
                optimized.add(cmd);
            }
        }

        // Emit remaining draw ranges at end of command stream
        while (rangeIndex < allRanges.size()) {
            emitDrawRange(allRanges.get(rangeIndex++), transformOpt, optimized);
        }

        // Emit residual transform to match expected GL state
        if (!transformOpt.isIdentity()) {
            transformOpt.emitPendingTransform(optimized);
        }

        return optimized.toArray(new DisplayListCommand[0]);
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
    static OptimizedListResult buildOptimizedDisplayList(
            List<DisplayListCommand> currentCommands,
            List<AccumulatedDraw> accumulatedDraws,
            List<AccumulatedLineDraw> accumulatedLineDraws,
            int glListId) {
        // Compile quad VBOs
        final Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers = compileFormatBasedVBOs(accumulatedDraws);

        // Compile line VBO
        final CompiledLineBuffer compiledLineBuffer = compileLineBuffer(accumulatedLineDraws);

        // Extract owned VBOs
        final VertexBuffer[] ownedVbos = extractOwnedVbos(compiledQuadBuffers, compiledLineBuffer);

        // Build optimized commands using the new unified path
        final DisplayListCommand[] optimized = buildOptimizedCommands(currentCommands, compiledQuadBuffers, compiledLineBuffer, glListId);

        return new OptimizedListResult(optimized, ownedVbos);
    }

    /**
     * Emit a draw range command, synchronizing transforms as needed.
     * Uses delta-based transform emission via TransformOptimizer.
     */
    private static void emitDrawRange(DrawRangeWithBuffer drwb, TransformOptimizer transformOpt,
                                       List<DisplayListCommand> output) {
        final DrawRange range = drwb.range();

        // Delta-based: only emit MultMatrix if transform differs from last emitted
        if (transformOpt.needsTransformForDraw(range.transform())) {
            transformOpt.emitTransformTo(output, range.transform());
        }

        output.add(new DrawRangeCmd(
            drwb.vbo(),
            range.startVertex(),
            range.vertexCount(),
            drwb.hasBrightness()
        ));
    }

    /**
     * Implementation of OptimizationContext that wraps transform optimizer.
     */
    @Desugar
    private record OptimizationContextImpl(
        TransformOptimizer transformOpt,
        List<DisplayListCommand> output
    ) implements OptimizationContext {
        @Override public Matrix4f getAccumulatedTransform() { return transformOpt.getAccumulated(); }
        @Override public void loadIdentity() { transformOpt.loadIdentity(); }
        @Override public void pushTransform() { transformOpt.pushTransform(); }
        @Override public void popTransform() { transformOpt.popTransform(); }
        @Override public void emitPendingTransform() { transformOpt.emitPendingTransform(output); }
        @Override public void emitTransformTo(Matrix4f target) { transformOpt.emitTransformTo(output, target); }
        @Override public void emit(DisplayListCommand cmd) { output.add(cmd); }
        @Override public void markAbsoluteMatrix() { transformOpt.markAbsoluteMatrix(); }
        @Override public boolean checkAndClearAbsoluteMatrix() { return transformOpt.checkAndClearAbsoluteMatrix(); }
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
   static VertexBuffer compileQuads(List<ModelQuadViewMutable> quads, CapturingTessellator.Flags flags) {
        // Select optimal predefined format from GTNHLib
        final VertexFormat format = selectOptimalFormat(flags);

        // Use VAOManager to create VAO with optimal format (falls back to VBO if unsupported)
        final VertexBuffer vao = VAOManager.createVAO(format, GL11.GL_QUADS);

        final ByteBuffer buffer = CapturingTessellator.quadsToBuffer(quads, format);

        vao.upload(buffer);

        return vao;
    }

}
