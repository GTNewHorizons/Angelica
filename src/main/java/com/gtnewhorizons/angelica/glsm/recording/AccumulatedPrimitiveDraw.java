package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.line.ModelLine;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.tri.ModelTriangle;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an accumulated primitive draw call during display list compilation.
 * Stores primitives (lines, triangles) from tessellator callback with their transform.
 * <p>
 * Unlike AccumulatedLineDraw which stores ImmediateModeRecorder.LineVertex (16-byte: pos+color),
 * this stores ModelPrimitiveView (32-byte: pos+color+tex+light+normal) from the tessellator.
 */
public class AccumulatedPrimitiveDraw {
    public final List<ModelPrimitiveView> primitives;
    public final Matrix4f transform;
    public final CapturingTessellator.Flags flags;
    public final int commandIndex;
    public final int matrixGeneration; // For batching: same generation = same transform context
    public final int stateGeneration; // Same value = no state commands between draws, can merge

    public AccumulatedPrimitiveDraw(List<ModelPrimitiveView> primitives, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int matrixGeneration) {
        this(primitives, transform, flags, commandIndex, matrixGeneration, 0);
    }

    public AccumulatedPrimitiveDraw(List<ModelPrimitiveView> primitives, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int matrixGeneration, int stateGeneration) {
        // Deep copy primitives - pooled primitives are reused after callback
        this.primitives = deepCopyPrimitives(primitives);
        this.transform = new Matrix4f(transform);
        this.flags = flags;
        this.commandIndex = commandIndex;
        this.matrixGeneration = matrixGeneration;
        this.stateGeneration = stateGeneration;
    }

    /**
     * Creates deep copies of all primitives in the list.
     * Required because CapturingTessellator pools and reuses primitive objects.
     */
    private static List<ModelPrimitiveView> deepCopyPrimitives(List<ModelPrimitiveView> source) {
        final int size = source.size();
        final List<ModelPrimitiveView> copies = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final ModelPrimitiveView prim = source.get(i);
            if (prim instanceof ModelLine ml) {
                copies.add(new ModelLine().copyFrom(ml));
            } else if (prim instanceof ModelTriangle mt) {
                copies.add(new ModelTriangle().copyFrom(mt));
            }
        }
        return copies;
    }

    @Override
    public String toString() {
        return String.format("AccumulatedPrimitiveDraw[primitives=%d, cmdIdx=%d, matrixGen=%d, stateGen=%d, mode=%d, flags=C%dT%dL%dN%d]",
            primitives.size(), commandIndex, matrixGeneration, stateGeneration, flags.drawMode,
            flags.hasColor ? 1 : 0, flags.hasTexture ? 1 : 0,
            flags.hasBrightness ? 1 : 0, flags.hasNormals ? 1 : 0);
    }
}
