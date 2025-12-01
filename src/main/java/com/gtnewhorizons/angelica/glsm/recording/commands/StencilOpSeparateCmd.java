package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilOpSeparate(face, sfail, dpfail, dppass)
 * Sets the stencil test actions for front and/or back faces separately.
 */
@Desugar
public record StencilOpSeparateCmd(int face, int sfail, int dpfail, int dppass) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilOpSeparate(0x%04X, 0x%04X, 0x%04X, 0x%04X)", face, sfail, dpfail, dppass);
    }
}
