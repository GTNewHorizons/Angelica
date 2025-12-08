package com.gtnewhorizons.angelica.glsm.recording;

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

    public AccumulatedDraw(List<ModelQuadViewMutable> quads, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex) {
        // Deep copy quads - pooled quads are reused after callback
        this.quads = ModelQuadUtil.deepCopyQuads(quads);

        this.transform = new Matrix4f(transform); // Snapshot for runtime application
        this.flags = flags;
        this.commandIndex = commandIndex;
    }

    @Override
    public String toString() {
        return String.format("AccumulatedDraw[quads=%d, cmdIdx=%d, flags=C%dT%dL%dN%d]",
            quads.size(), commandIndex, flags.hasColor ? 1 : 0, flags.hasTexture ? 1 : 0, flags.hasBrightness ? 1 : 0, flags.hasNormals ? 1 : 0);
    }
}
