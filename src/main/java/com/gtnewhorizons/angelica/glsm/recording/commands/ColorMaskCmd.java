package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glColorMask(red, green, blue, alpha)
 * Enables or disables writing to color buffer channels.
 */
@Desugar
public record ColorMaskCmd(boolean red, boolean green, boolean blue, boolean alpha) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glColorMask(red, green, blue, alpha);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Color mask affects rendering output
    }

    @Override
    public @NotNull String toString() {
        return String.format("ColorMask(r=%s, g=%s, b=%s, a=%s)", red, green, blue, alpha);
    }
}
