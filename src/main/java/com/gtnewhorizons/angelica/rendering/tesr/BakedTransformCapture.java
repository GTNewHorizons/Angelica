package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.client.rendering.ModelViewDelta;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

public final class BakedTransformCapture extends DirectTessellator {

    private final ModelViewDelta delta = new ModelViewDelta();
    private final Matrix4f deltaMatrix = new Matrix4f();
    private final Vector3f scratch = new Vector3f();
    private Matrix4fc transform;
    private int firstColor;
    private boolean sawRun;
    private boolean colorUniform = true;

    public BakedTransformCapture(int capacity) {
        super(memAlloc(capacity));
    }

    public void begin() {
        TessellatorManager.startCapturingDirect(this);
        delta.snapshot();
        sawRun = false;
        colorUniform = true;
    }

    public TemplateBuffer end() {
        final TemplateBuffer template = colorUniform ? TemplateCapture.toTemplate(this) : null;
        TessellatorManager.stopCapturingDirect();
        return template;
    }

    @Override
    protected int interceptDraw(Tessellator tessellator) {
        final Color4 color = GLStateManager.getColor();
        final int packed = ColorABGR.pack(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        if (!sawRun) {
            sawRun = true;
            firstColor = packed;
        } else if (packed != firstColor) {
            colorUniform = false;
        }
        transform = delta.deltaOrNull(deltaMatrix);
        try {
            return super.interceptDraw(tessellator);
        } finally {
            transform = null;
        }
    }

    // While capturing, Tessellator.instance is this tessellator, so vanilla code (e.g. RenderBlocks.renderBlockAsItem)
    // writes vertices directly instead of going through interceptDraw; bake the transform in here as well.
    @Override
    public void addVertex(double x, double y, double z) {
        final Matrix4fc direct = transform == null ? delta.deltaOrNull(deltaMatrix) : null;
        if (direct == null) {
            super.addVertex(x, y, z);
            return;
        }
        scratch.set((float) (x + xOffset), (float) (y + yOffset), (float) (z + zOffset)).mulPosition(direct);
        super.addVertex(scratch.x - xOffset, scratch.y - yOffset, scratch.z - zOffset);
    }

    @Override
    public void setNormal(float nx, float ny, float nz) {
        final Matrix4fc direct = transform == null ? delta.deltaOrNull(deltaMatrix) : null;
        if (direct == null) {
            super.setNormal(nx, ny, nz);
            return;
        }
        scratch.set(nx, ny, nz).mulDirection(direct).normalize();
        super.setNormal(scratch.x, scratch.y, scratch.z);
    }

    @Override
    protected long writeVertexData(VertexFormat format, int[] rawBuffer, int rawBufferIndex) {
        if (transform != null) {
            return format.writeToBuffer0(writePtr, rawBuffer, rawBufferIndex, transform, scratch);
        }
        return super.writeVertexData(format, rawBuffer, rawBufferIndex);
    }
}
