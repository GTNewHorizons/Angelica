package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL13;

/**
 * Command: glActiveTexture(texture)
 * Selects which texture unit is active during display list playback.
 */
@Desugar
public record ActiveTextureCmd(int texture) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glActiveTexture(texture);
    }

    @Override
    public @NotNull String toString() {
        return String.format("ActiveTexture(GL_TEXTURE%d)", texture - GL13.GL_TEXTURE0);
    }
}
