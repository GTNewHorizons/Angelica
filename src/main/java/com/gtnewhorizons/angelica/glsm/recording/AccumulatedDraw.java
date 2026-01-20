package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.CallbackTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an accumulated draw call during display list compilation.
 * Matrix-as-Data Architecture: Stores untransformed vertices + captured matrix.
 * The matrix will be applied at runtime, not baked into vertices.
 */
public final class AccumulatedDraw {
    public final Matrix4f transform;
    public final VertexFormat format;
    public final List<ByteBuffer> drawBuffers;
    public final int drawMode;
    public final int commandIndex; // Position in command list for state tracking
    public final int stateGeneration; // Position in command list for state tracking
    public RestoreData restoreData;

    public AccumulatedDraw(DirectTessellator tessellator, Matrix4f transform, int commandIndex, int stateGeneration, boolean copyLast) {
        this.transform = transform; // Snapshot for runtime application
        this.format = tessellator.getVertexFormat();
        this.drawMode = tessellator.drawMode;
        this.drawBuffers = new ArrayList<>();
        this.drawBuffers.add(tessellator.allocateBufferCopy());
        this.commandIndex = commandIndex;
        this.stateGeneration = stateGeneration;

        if (copyLast) {
            this.restoreData = new RestoreData(tessellator);
        }
    }


    public void mergeDraw(DirectTessellator tessellator, boolean copyLast) {
        this.drawBuffers.add(tessellator.allocateBufferCopy());

        if (copyLast) {
            this.restoreData = new RestoreData(tessellator);
        }
    }

    /**
     * Holds last vertex attribute values for GL state restoration after VBO draw.
     * Used to sync GLSM cache with actual GL state after VBO rendering.
     */
    public static final class RestoreData {
        public final int lastColor;
        public final int lastNormal;
        public final float lastTexCoordU;
        public final float lastTexCoordV;
        public final int flags;

        public RestoreData(DirectTessellator tessellator) {
            this.lastColor = tessellator.getPackedColor();
            this.lastNormal = tessellator.getPackedNormal();
            this.lastTexCoordU = (float) tessellator.getLastTextureU();
            this.lastTexCoordV = (float) tessellator.getLastTextureV();
            this.flags = tessellator.getVertexFormat().getVertexFlags();
        }
    }
}
