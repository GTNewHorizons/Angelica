package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glDepthFunc(func)
 * Specifies the depth comparison function.
 */
@Desugar
public record DepthFuncCmd(int func) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glDepthFunc(func);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Depth function affects rendering
    }

    @Override
    public @NotNull String toString() {
        return "DepthFunc(" + GLDebug.getComparisonFuncName(func) + ")";
    }
}
