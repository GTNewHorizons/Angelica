package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;

public class VertexArrayQuadView implements ModelQuadView {
    private final ChunkVertexEncoder.Vertex[] vertices;
    private int trueNormal;
    private int blockX, blockY, blockZ;
    private ModelQuadFacing lightFace;
    private int flags;

    public VertexArrayQuadView(ChunkVertexEncoder.Vertex[] vertices) {
        this.vertices = vertices;
    }

    public void setup(int trueNormal, int blockX, int blockY, int blockZ) {
        this.trueNormal = trueNormal;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.lightFace = null; // Reset cached lightFace
        this.flags = 0;
    }

    @Override
    public float getX(int idx) {
        return vertices[idx].x - blockX;
    }

    @Override
    public float getY(int idx) {
        return vertices[idx].y - blockY;
    }

    @Override
    public float getZ(int idx) {
        return vertices[idx].z - blockZ;
    }

    @Override
    public int getColor(int idx) {
        return vertices[idx].color;
    }

    @Override
    public float getTexU(int idx) {
        return vertices[idx].u;
    }

    @Override
    public float getTexV(int idx) {
        return vertices[idx].v;
    }

    @Override
    public int getLight(int idx) {
        return vertices[idx].light;
    }

    @Override
    public int getFlags() {
        if (flags == 0) {
            flags = ModelQuadFlags.getQuadFlags(this, getLightFace());
        }
        return flags;
    }

    @Override
    public int getColorIndex() {
        return -1;
    }

    @Override
    public Object celeritas$getSprite() {
        return null;
    }

    @Override
    public ModelQuadFacing getLightFace() {
        if (lightFace == null) {
            lightFace = computeLightFace(trueNormal);
        }
        return lightFace;
    }

    @Override
    public ModelQuadFacing getNormalFace() {
        return QuadUtil.findNormalFace(trueNormal);
    }

    @Override
    public int getForgeNormal(int idx) {
        return vertices[idx].vanillaNormal;
    }

    @Override
    public int getComputedFaceNormal() {
        return trueNormal;
    }

    /**
     * Gets the cull face of a quad. This is distinct from the light face, as it will not be set for non-axis-aligned
     * and non-block-grid-aligned quads (i.e. quads on the surface of the 1x1x1 cube).
     * @return the cull face if present, or UNASSIGNED
     */
    public ModelQuadFacing getCullFace() {
        // In modern, only quads that are aligned to the block grid can have a cull face, as otherwise
        // they should not participate in face culling (an adjacent block wouldn't obscure an inset quad)
        if ((getFlags() & ModelQuadFlags.IS_ALIGNED) == 0) {
            return ModelQuadFacing.UNASSIGNED;
        }

        return getNormalFace();
    }

    private static ModelQuadFacing computeLightFace(int normal) {
        final float nx = NormI8.unpackX(normal);
        final float ny = NormI8.unpackY(normal);
        final float nz = NormI8.unpackZ(normal);

        final float absX = Math.abs(nx);
        final float absY = Math.abs(ny);
        final float absZ = Math.abs(nz);

        if (absY >= absX && absY >= absZ) {
            return ny >= 0 ? ModelQuadFacing.POS_Y : ModelQuadFacing.NEG_Y;
        } else if (absX >= absZ) {
            return nx >= 0 ? ModelQuadFacing.POS_X : ModelQuadFacing.NEG_X;
        } else {
            return nz >= 0 ? ModelQuadFacing.POS_Z : ModelQuadFacing.NEG_Z;
        }
    }
}
