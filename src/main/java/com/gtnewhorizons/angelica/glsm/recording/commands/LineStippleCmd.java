package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLineStipple(factor, pattern)
 * Sets the line stipple pattern.
 */
@Desugar
public record LineStippleCmd(int factor, short pattern) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLineStipple(factor, pattern);
    }

    @Override
    public boolean breaksBatch() {
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("LineStipple(%d, 0x%04X)", factor, pattern & 0xFFFF);
    }
}
