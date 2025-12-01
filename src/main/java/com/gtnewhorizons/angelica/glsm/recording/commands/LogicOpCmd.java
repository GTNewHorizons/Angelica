package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLogicOp(opcode)
 * Specifies a logical pixel operation for rendering.
 * Common values: GL_COPY (default), GL_XOR, GL_AND, GL_OR, etc.
 */
@Desugar
public record LogicOpCmd(int opcode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLogicOp(opcode);
    }

    @Override
    public @NotNull String toString() {
        return String.format("LogicOp(%s)", GLDebug.getLogicOpName(opcode));
    }
}
