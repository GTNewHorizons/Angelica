package com.gtnewhorizons.angelica.glsm.recording;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Represents an accumulated draw call during display list compilation.
 * Matrix-as-Data Architecture: Stores untransformed vertices + captured matrix.
 * The matrix will be applied at runtime, not baked into vertices.
 */
public class AccumulatedDraw {
    public final List<ModelQuadViewMutable> quads;
    public final Matrix4f transform;
    public final CapturingTessellator.Flags flags;
    public final int commandIndex; // Position in command list for state tracking
    public final int matrixGeneration; // For batching: same generation = same transform context
    public final int stateGeneration; // Same value = no state commands between draws, can merge

    /**
     * Last vertex restoration data for immediate mode draws.
     * Null for tessellator draws (no restoration needed).
     */
    public final RestoreData restoreData;

    /**
     * Holds last vertex attribute values for GL state restoration after VBO draw.
     * Used to sync GLSM cache with actual GL state after VBO rendering.
     */
    @Desugar
    public record RestoreData(
        float lastColorR, float lastColorG, float lastColorB, float lastColorA,
        float lastNormalX, float lastNormalY, float lastNormalZ,
        float lastTexCoordS, float lastTexCoordT) {}

    public AccumulatedDraw(List<ModelQuadViewMutable> quads, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int matrixGeneration) {
        this(quads, transform, flags, commandIndex, matrixGeneration, 0, null);
    }

    public AccumulatedDraw(List<ModelQuadViewMutable> quads, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int matrixGeneration, int stateGeneration) {
        this(quads, transform, flags, commandIndex, matrixGeneration, stateGeneration, null);
    }

    public AccumulatedDraw(List<ModelQuadViewMutable> quads, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int matrixGeneration, int stateGeneration, RestoreData restoreData) {
        // Deep copy quads - pooled quads are reused after callback
        this.quads = ModelQuadUtil.deepCopyQuads(quads);

        this.transform = new Matrix4f(transform); // Snapshot for runtime application
        this.flags = flags;
        this.commandIndex = commandIndex;
        this.matrixGeneration = matrixGeneration;
        this.stateGeneration = stateGeneration;
        this.restoreData = restoreData;
    }

    @Override
    public String toString() {
        return String.format("AccumulatedDraw[quads=%d, cmdIdx=%d, matrixGen=%d, stateGen=%d, flags=C%dT%dL%dN%d, restore=%s]",
            quads.size(), commandIndex, matrixGeneration, stateGeneration, flags.hasColor ? 1 : 0, flags.hasTexture ? 1 : 0, flags.hasBrightness ? 1 : 0, flags.hasNormals ? 1 : 0,
            restoreData != null ? "yes" : "no");
    }
}
