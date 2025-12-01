package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilMask(mask)
 * Controls the writing of individual bits in the stencil planes.
 */
@Desugar
public record StencilMaskCmd(int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilMask(mask);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Stencil mask affects rendering
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilMask(0x%08X)", mask);
    }
}
