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
}
