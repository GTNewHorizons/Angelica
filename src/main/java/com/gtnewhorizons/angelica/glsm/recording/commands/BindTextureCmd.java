package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glBindTexture(target, texture)
 * Binds a texture to a target during display list playback.
 */
@Desugar
public record BindTextureCmd(int target, int texture) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glBindTexture(target, texture);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Texture changes affect all subsequent draws
    }

    @Override
    public @NotNull String toString() {
        return String.format("BindTexture(%s, %d)", GLDebug.getTextureTargetName(target), texture);
    }
}
