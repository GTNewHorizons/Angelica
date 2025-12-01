package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilFunc(func, ref, mask)
 * Sets the stencil test function and reference value.
 */
@Desugar
public record StencilFuncCmd(int func, int ref, int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilFunc(func, ref, mask);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilFunc(0x%04X, %d, 0x%08X)", func, ref, mask);
    }
}
