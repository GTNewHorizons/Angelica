package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLineWidth(width)
 * Sets the width of rasterized lines.
 */
@Desugar
public record LineWidthCmd(float width) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLineWidth(width);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("LineWidth(%.2f)", width);
    }
}
