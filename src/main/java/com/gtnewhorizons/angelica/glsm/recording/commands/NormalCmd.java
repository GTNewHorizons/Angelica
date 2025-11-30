package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glNormal3f(nx, ny, nz)
 * Sets the current normal vector.
 */
@Desugar
public record NormalCmd(float nx, float ny, float nz) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glNormal3f(nx, ny, nz);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Normal(%.2f, %.2f, %.2f)", nx, ny, nz);
    }
}
