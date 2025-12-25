package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an accumulated line draw call during display list compilation.
 * Similar to AccumulatedDraw but for line primitives.
 * Stores untransformed line vertices + captured matrix.
 */
public class AccumulatedLineDraw {
    public final List<ImmediateModeRecorder.LineVertex> lines;
    public final Matrix4f transform;
    public final CapturingTessellator.Flags flags;
    public final int commandIndex; // Position in command list for state tracking
    public final int stateGeneration; // Same value = no state commands between draws, can merge

    public AccumulatedLineDraw(List<ImmediateModeRecorder.LineVertex> lines, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex) {
        this(lines, transform, flags, commandIndex, 0);
    }

    public AccumulatedLineDraw(List<ImmediateModeRecorder.LineVertex> lines, Matrix4f transform, CapturingTessellator.Flags flags, int commandIndex, int stateGeneration) {
        // Copy lines - the source list may be cleared
        this.lines = new ArrayList<>(lines);
        this.transform = new Matrix4f(transform); // Snapshot for runtime application
        this.flags = flags;
        this.commandIndex = commandIndex;
        this.stateGeneration = stateGeneration;
    }

    @Override
    public String toString() {
        return String.format("AccumulatedLineDraw[lines=%d, cmdIdx=%d, flags=C%dT%dL%dN%d]",
            lines.size() / 2, commandIndex, flags.hasColor ? 1 : 0, flags.hasTexture ? 1 : 0, flags.hasBrightness ? 1 : 0, flags.hasNormals ? 1 : 0);
    }
}
