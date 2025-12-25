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
     * Handle this command during display list optimization.
     *
     * <p>Commands override this to implement their optimization behavior:
     * <ul>
     *   <li>Matrix transforms: accumulate into the optimizer, return false</li>
     *   <li>Push/Pop: update stack, return true to emit</li>
     *   <li>Barriers: emit pending transforms, return true to emit</li>
     *   <li>State changes: return true to emit</li>
     * </ul>
     *
     * @param ctx The optimization context providing access to transform state
     * @return true if this command should be added to output, false if handled internally
     */
    default boolean handleOptimization(OptimizationContext ctx) {
        return true;
    }
}
