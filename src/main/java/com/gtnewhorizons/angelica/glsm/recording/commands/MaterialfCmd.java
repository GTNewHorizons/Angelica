package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glMaterialf(face, pname, val)
 * Sets a single float material parameter.
 */
@Desugar
public record MaterialfCmd(int face, int pname, float val) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glMaterialf(face, pname, val);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Materialf(%s, %s, %.2f)",
            GLDebug.getFaceName(face), GLDebug.getMaterialPnameName(pname), val);
    }
}
