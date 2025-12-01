package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glEnable(cap)
 * Enables a GL capability during display list playback.
 */
@Desugar
public record EnableCmd(int cap) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glEnable(cap);
    }

    @Override
    public @NotNull String toString() {
        return "Enable(" + GLDebug.getCapabilityName(cap) + ")";
    }
}
