package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * Command: Render a VBO or VAO
 * Created from Tessellator.draw() calls during display list compilation.
 *
 */
@Desugar
public record DrawVBOCmd(VertexBuffer vbo, boolean hasBrightness) implements DisplayListCommand {
    @Override
    public void execute() {
        vbo.render();
    }

    @Override
    public void delete() {
        vbo.close();
    }

    @Override
    public @NotNull String toString() {
        return "DrawVBO(vertexCount=" + vbo.getVertexCount() + ", hasBrightness=" + hasBrightness + ")";
    }
}
