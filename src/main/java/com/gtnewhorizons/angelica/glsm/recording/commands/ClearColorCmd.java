package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glClearColor(float red, float green, float blue, float alpha)
 * Sets the clear color for glClear(GL_COLOR_BUFFER_BIT).
 */
@Desugar
public record ClearColorCmd(float red, float green, float blue, float alpha) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glClearColor(red, green, blue, alpha);
    }

    @Override
    public @NotNull String toString() {
        return String.format("ClearColor(%.2f, %.2f, %.2f, %.2f)", red, green, blue, alpha);
    }
}
