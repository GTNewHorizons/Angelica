package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glPointSize(size)
 * Sets the diameter of rasterized points.
 */
@Desugar
public record PointSizeCmd(float size) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPointSize(size);
    }

    @Override
    public @NotNull String toString() {
        return String.format("PointSize(%.2f)", size);
    }
}
