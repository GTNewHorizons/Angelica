package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilMaskSeparate(face, mask)
 * Controls the writing of individual bits in the stencil planes for front and/or back faces.
 */
@Desugar
public record StencilMaskSeparateCmd(int face, int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilMaskSeparate(face, mask);
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilMaskSeparate(0x%04X, 0x%08X)", face, mask);
    }
}
