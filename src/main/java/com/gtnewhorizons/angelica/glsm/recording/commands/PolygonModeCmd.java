package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glPolygonMode(face, mode)
 * Selects a polygon rasterization mode.
 */
@Desugar
public record PolygonModeCmd(int face, int mode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPolygonMode(face, mode);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("PolygonMode(0x%04X, 0x%04X)", face, mode);
    }
}
