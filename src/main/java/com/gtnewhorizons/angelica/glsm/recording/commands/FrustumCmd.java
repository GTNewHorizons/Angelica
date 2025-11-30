package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glFrustum(left, right, bottom, top, zNear, zFar)
 * Sets up a perspective projection matrix using a frustum.
 */
@Desugar
public record FrustumCmd(double left, double right, double bottom, double top, double zNear, double zFar) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Frustum(%.2f, %.2f, %.2f, %.2f, %.2f, %.2f)", left, right, bottom, top, zNear, zFar);
    }
}
