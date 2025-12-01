package com.gtnewhorizons.angelica.glsm.recording;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;

/**
 * Represents a compiled display list with dual representation:
 * - Optimized: Uses shared VBOs with DrawRangeCmd, collapsed transforms - for normal playback
 * - Unoptimized: Original commands with matrix transforms, DrawRangeCmd - for nested display list calls
 *
 * <p>Both paths share the same VBOs for all geometry (quads and lines) via DrawRangeCmd.
 * The ownedVbos array contains these shared VBOs that are referenced by DrawRangeCmds
 * in both arrays.
 *
 * <p><b>Note:</b> The unoptimized path is only built when {@code -Dangelica.debugDisplayLists=true}
 * is set. In production, the unoptimized array is empty to save CPU time and memory.
 *
 */
@Desugar
public record CompiledDisplayList(
    DisplayListCommand[] optimized,
    DisplayListCommand[] unoptimized,
    VertexBuffer[] ownedVbos
) {
    /**
     * Render this display list by executing all optimized commands.
     */
    public void render() {
        for (DisplayListCommand cmd : optimized) {
            cmd.execute();
        }
    }

    /**
     * Execute unoptimized commands (preserves all matrix commands).
     * Generally not needed since we track relative transforms, but kept for debugging.
     *
     * <p><b>Note:</b> This method does nothing unless {@code -Dangelica.debugDisplayLists=true}
     * is set, since the unoptimized array will be empty in production.
     */
    public void executeUnoptimized() {
        for (DisplayListCommand cmd : unoptimized) {
            cmd.execute();
        }
    }

    /**
     * Delete all resources held by this display list.
     * <p>
     * Deletes all shared VBOs from ownedVbos (quads + lines).
     * Both paths use DrawRangeCmd which references these shared VBOs.
     */
    public void delete() {
        for (VertexBuffer vbo : ownedVbos) {
            vbo.close();
        }
    }

    /**
     * Get the unoptimized commands for inlining into another display list (nested lists).
     * Returns unoptimized version because nested calls need matrix state preserved.
     *
     * <p><b>Note:</b> Returns an empty array unless {@code -Dangelica.debugDisplayLists=true}
     * is set. In production, unoptimized commands are not built to save CPU time and memory.
     *
     * @return The unoptimized command array, or empty array if debug flag is not set
     */
    public DisplayListCommand[] getCommands() {
        return unoptimized;
    }
}
