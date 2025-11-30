package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glShadeModel(mode)
 * Selects flat or smooth shading.
 */
@Desugar
public record ShadeModelCmd(int mode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glShadeModel(mode);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Shading model affects rendering
    }

    @Override
    public @NotNull String toString() {
        return "ShadeModel(" + GLDebug.getShadeModelName(mode) + ")";
    }
}
