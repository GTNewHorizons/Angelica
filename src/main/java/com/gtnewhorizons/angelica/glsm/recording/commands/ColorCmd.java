package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glColor3f() or glColor4f()
 * Stores RGBA as floats (alpha=1.0 for Color3f)
 */
@Desugar
public record ColorCmd(float r, float g, float b, float a) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glColor4f(r, g, b, a);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Color(%.2f, %.2f, %.2f, %.2f)", r, g, b, a);
    }
}
