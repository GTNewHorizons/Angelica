package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glCullFace(mode)
 * Specifies which polygon faces to cull (GL_FRONT, GL_BACK, or GL_FRONT_AND_BACK).
 */
@Desugar
public record CullFaceCmd(int mode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glCullFace(mode);
    }

    @Override
    public @NotNull String toString() {
        return String.format("CullFace(%s)", GLDebug.getCullFaceName(mode));
    }
}
