package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glDepthMask(flag)
 * Enables or disables writing to the depth buffer.
 */
@Desugar
public record DepthMaskCmd(boolean flag) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glDepthMask(flag);
    }

    @Override
    public @NotNull String toString() {
        return String.format("DepthMask(%s)", flag);
    }
}
