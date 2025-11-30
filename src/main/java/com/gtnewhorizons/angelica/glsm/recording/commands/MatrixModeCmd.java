package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glMatrixMode(mode)
 * Sets the current matrix mode (MODELVIEW, PROJECTION, or TEXTURE).
 */
@Desugar
public record MatrixModeCmd(int mode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glMatrixMode(mode);
    }

    @Override
    public @NotNull String toString() {
        return String.format("MatrixMode(%s)", GLDebug.getMatrixModeName(mode));
    }
}
