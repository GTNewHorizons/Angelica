package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glClear(mask)
 * Clears buffers to preset values.
 */
@Desugar
public record ClearCmd(int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glClear(mask);
    }

    @Override
    public @NotNull String toString() {
        return "Clear(" + GLDebug.getClearMaskString(mask) + ")";
    }
}
