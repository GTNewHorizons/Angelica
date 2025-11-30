package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glTexParameteri(target, pname, param)
 * Sets an integer texture parameter.
 */
@Desugar
public record TexParameteriCmd(int target, int pname, int param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glTexParameteri(target, pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("TexParameteri(%s, %s, %d)",
            GLDebug.getTextureTargetName(target), GLDebug.getTexturePnameName(pname), param);
    }
}
