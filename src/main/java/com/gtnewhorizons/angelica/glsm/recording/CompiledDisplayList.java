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
     * Generally not needed since we track relative transforms, but kept for visibility.
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
     */
    public DisplayListCommand[] getCommands() {
        return unoptimized;
    }
}
