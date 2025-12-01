package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilFuncSeparate(face, func, ref, mask)
 * Sets the stencil test function for front and/or back faces separately.
 */
@Desugar
public record StencilFuncSeparateCmd(int face, int func, int ref, int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilFuncSeparate(face, func, ref, mask);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilFuncSeparate(0x%04X, 0x%04X, %d, 0x%08X)", face, func, ref, mask);
    }
}
