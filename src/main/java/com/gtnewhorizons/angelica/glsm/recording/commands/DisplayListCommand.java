package com.gtnewhorizons.angelica.glsm.recording.commands;

/**
 * A single command recorded during display list compilation.
 * Commands are executed in sequence during glCallList() to replay the display list.
 */
public interface DisplayListCommand {
    /**
     * Execute this command by calling the appropriate GL function.
     */
    void execute();

    /**
     * Release any resources held by this command (e.g., VBOs).
     * Called when the display list is deleted.
     */
    default void delete() {
        // Most commands don't hold resources
    }

    /**
     * Returns true if this command changes GL state in a way that requires
     * pending draws to be flushed before executing this command.
     * Examples: texture binding, blend state, depth state changes.
     *
     * <p>This is used by the display list optimizer to determine when to
     * flush batched draws. Commands that return true will cause the batcher
     * to emit any pending draws before this command is added to the output.</p>
     *
     * @return true if this command breaks draw batching
     */
    default boolean breaksBatch() {
        return false;
    }

    /**
     * Returns true if this barrier command requires the MODELVIEW transform to be
     * synchronized with the accumulated state before execution.
     *
     * <p>This should return true for commands where the GL matrix state matters,
     * such as CallListCmd (nested lists use current GL state) or PushAttribCmd
     * with GL_TRANSFORM_BIT (saves the current matrix).</p>
     *
     * <p>Commands like PushAttribCmd with only GL_COLOR_BUFFER_BIT don't need
     * transform sync because they don't interact with the matrix stack.</p>
     *
     * @return true if transform must be synced to accumulated before this barrier
     */
    default boolean needsTransformSync() {
        return false;
    }

    /**
     * Handle this command during display list optimization.
     *
     * <p>Commands override this to implement their optimization behavior:
     * <ul>
     *   <li>Matrix transforms: accumulate into the optimizer, return false</li>
     *   <li>Push/Pop: flush batcher, update stack, return true to emit</li>
     *   <li>Barriers: flush batcher and transforms, return true to emit</li>
     *   <li>State changes: optionally flush batcher, return true to emit</li>
     * </ul>
     *
     * @param ctx The optimization context providing access to transform/batcher state
     * @return true if this command should be added to output, false if handled internally
     */
    default boolean handleOptimization(OptimizationContext ctx) {
        // Default: flush if needed, then emit
        if (breaksBatch()) {
            ctx.flushBatcher();
        }
        return true;
    }
}
