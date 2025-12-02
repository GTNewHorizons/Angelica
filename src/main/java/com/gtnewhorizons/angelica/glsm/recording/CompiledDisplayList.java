package com.gtnewhorizons.angelica.glsm.recording;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;

/**
 * Represents a compiled display list.
 *
 * <p>Contains either optimized commands (collapsed transforms, merged draw ranges) in production,
 * or unoptimized commands (original transforms preserved) when debug mode is enabled.
 *
 * <p>The ownedVbos array contains VBOs that are referenced by DrawRangeCmds.
 */
@Desugar
public record CompiledDisplayList(
    DisplayListCommand[] commands,
    VertexBuffer[] ownedVbos
) {
    /**
     * Render this display list by executing all commands.
     */
    public void render() {
        for (DisplayListCommand cmd : commands) {
            cmd.execute();
        }
    }

    /**
     * Delete all resources held by this display list.
     */
    public void delete() {
        for (DisplayListCommand cmd : commands) {
            cmd.delete();
        }
        for (VertexBuffer vbo : ownedVbos) {
            vbo.close();
        }
    }

    /**
     * Get the commands for inlining into another display list (nested lists).
     *
     * @return The command array
     */
    public DisplayListCommand[] getCommands() {
        return commands;
    }
}
