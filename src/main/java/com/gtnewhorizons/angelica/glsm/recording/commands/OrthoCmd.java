package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glOrtho(left, right, bottom, top, zNear, zFar)
 * Sets up an orthographic projection matrix.
 */
@Desugar
public record OrthoCmd(double left, double right, double bottom, double top, double zNear, double zFar) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Ortho(%.2f, %.2f, %.2f, %.2f, %.2f, %.2f)", left, right, bottom, top, zNear, zFar);
    }
}
