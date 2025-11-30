package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glTexParameterf(target, pname, param)
 * Sets a float texture parameter.
 */
@Desugar
public record TexParameterfCmd(int target, int pname, float param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glTexParameterf(target, pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("TexParameterf(%s, %s, %.2f)",
            GLDebug.getTextureTargetName(target), GLDebug.getTexturePnameName(pname), param);
    }
}
