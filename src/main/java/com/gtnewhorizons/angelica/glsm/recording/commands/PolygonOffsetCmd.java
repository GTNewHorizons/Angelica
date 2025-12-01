package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glPolygonOffset(factor, units)
 * Sets the scale and units used to calculate depth values.
 */
@Desugar
public record PolygonOffsetCmd(float factor, float units) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPolygonOffset(factor, units);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("PolygonOffset(%.4f, %.4f)", factor, units);
    }
}
