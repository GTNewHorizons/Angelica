package com.gtnewhorizons.angelica.glsm.recording;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Represents a compiled display list with dual representation:
 * - Optimized: Baked transforms, stripped matrix commands - for normal playback
 * - Unoptimized: Original commands with matrix transforms - for nested display list calls
 *
 * Both lists are immutable after construction.
 */
@Desugar
public record CompiledDisplayList(List<DisplayListCommand> optimized, List<DisplayListCommand> unoptimized) {
    /**
     * Canonical constructor ensures lists are immutable.
     */
    public CompiledDisplayList {
        optimized = ImmutableList.copyOf(optimized);
        unoptimized = ImmutableList.copyOf(unoptimized);
    }

    /**
     * Render this display list by executing all optimized commands.
     */
    public void render() {
        final int size = optimized.size();
        for (int i = 0; i < size; i++) {
            optimized.get(i).execute();
        }
    }

    /**
     * Execute unoptimized commands (preserves all matrix commands).
     * Generally not needed since we track relative transforms, but kept for visibility.
     */
    public void executeUnoptimized() {
        final int size = unoptimized.size();
        for (int i = 0; i < size; i++) {
            unoptimized.get(i).execute();
        }
    }

    /**
     * Delete all resources held by this display list (e.g., VBOs).
     * Only deletes optimized resources (unoptimized shares same VBOs).
     */
    public void delete() {
        final int size = optimized.size();
        for (int i = 0; i < size; i++) {
            optimized.get(i).delete();
        }
    }

    /**
     * Get the unoptimized commands for inlining into another display list (nested lists).
     * Returns unoptimized version because nested calls need matrix state preserved.
     */
    public List<DisplayListCommand> getCommands() {
        return unoptimized;
    }
}
