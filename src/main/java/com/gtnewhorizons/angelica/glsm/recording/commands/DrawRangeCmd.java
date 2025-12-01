package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * Command: Draw a range of vertices from a shared VBO.
 * <p>
 * DrawRangeCmd references a shared VBO owned by CompiledDisplayList.ownedVbos.
 * Multiple DrawRangeCmds can reference different ranges within the same VBO,
 * enabling format-based batching where all draws with the same vertex format
 * share a single VBO.
 * <p>
 * Transform is NOT stored here - handled by TransformOptimizer emitting
 * MultMatrixCmd before this command during optimization.
 */
@Desugar
public record DrawRangeCmd(
    VertexBuffer sharedVbo,
    int startVertex,
    int vertexCount,
    boolean hasBrightness
) implements DisplayListCommand {

    @Override
    public void execute() {
        sharedVbo.setupState();
        sharedVbo.draw(startVertex, vertexCount);
        sharedVbo.cleanupState();
    }

    @Override
    public void delete() {
        // No-op: VBO lifetime is managed by CompiledDisplayList.ownedVbos
    }

    @Override
    public @NotNull String toString() {
        return "DrawRange(start=" + startVertex + ", count=" + vertexCount + ", hasBrightness=" + hasBrightness + ")";
    }
}
