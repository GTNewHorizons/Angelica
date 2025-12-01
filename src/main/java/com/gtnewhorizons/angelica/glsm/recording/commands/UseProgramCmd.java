package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glUseProgram(program)
 * Installs a program object as part of current rendering state.
 */
@Desugar
public record UseProgramCmd(int program) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glUseProgram(program);
    }

    @Override
    public boolean breaksBatch() {
        return true;  // Shader change affects rendering
    }

    @Override
    public @NotNull String toString() {
        return String.format("UseProgram(%d)", program);
    }
}
