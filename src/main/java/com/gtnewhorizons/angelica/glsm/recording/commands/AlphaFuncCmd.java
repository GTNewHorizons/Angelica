package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glAlphaFunc(func, ref)
 * Sets the alpha test function and reference value.
 */
@Desugar
public record AlphaFuncCmd(int func, float ref) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glAlphaFunc(func, ref);
    }

    @Override
    public @NotNull String toString() {
        return String.format("AlphaFunc(%s, %.2f)", GLDebug.getComparisonFuncName(func), ref);
    }
}
