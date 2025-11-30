package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glColorMaterial(face, mode)
 * Specifies which material parameters track the current color.
 */
@Desugar
public record ColorMaterialCmd(int face, int mode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glColorMaterial(face, mode);
    }

    @Override
    public @NotNull String toString() {
        return String.format("ColorMaterial(%s, %s)",
            GLDebug.getFaceName(face), GLDebug.getColorMaterialModeName(mode));
    }
}
