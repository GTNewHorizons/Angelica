package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glDisable(cap)
 * Disables a GL capability during display list playback.
 */
@Desugar
public record DisableCmd(int cap) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glDisable(cap);
    }

    @Override
    public @NotNull String toString() {
        return "Disable(" + GLDebug.getCapabilityName(cap) + ")";
    }
}
