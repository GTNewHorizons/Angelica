package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glViewport(x, y, width, height)
 * Sets the viewport dimensions for rendering.
 */
@Desugar
public record ViewportCmd(int x, int y, int width, int height) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glViewport(x, y, width, height);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Viewport affects where draws appear
    }

    @Override
    public @NotNull String toString() {
        return String.format("Viewport(%d, %d, %d, %d)", x, y, width, height);
    }
}
