package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glStencilOp(fail, zfail, zpass)
 * Sets the stencil test actions.
 */
@Desugar
public record StencilOpCmd(int fail, int zfail, int zpass) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glStencilOp(fail, zfail, zpass);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("StencilOp(0x%04X, 0x%04X, 0x%04X)", fail, zfail, zpass);
    }
}
