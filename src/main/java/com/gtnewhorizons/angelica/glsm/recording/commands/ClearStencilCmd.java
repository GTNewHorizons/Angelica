package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glClearStencil(s)
 * Sets the clear value for the stencil buffer.
 */
@Desugar
public record ClearStencilCmd(int s) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glClearStencil(s);
    }

    @Override
    public @NotNull String toString() {
        return String.format("ClearStencil(%d)", s);
    }
}
