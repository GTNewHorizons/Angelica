package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;

public final class GpuCulledMultiDrawBatch extends MultiDrawBatch {

    private final GpuTerrainCuller culler;
    private int drawStart;

    GpuCulledMultiDrawBatch(GpuTerrainCuller culler) {
        super(MAX_COMMAND_COUNT);
        this.culler = culler;
    }

    void prepare(int drawStart, int drawCount, int maxElementCount) {
        this.drawStart = drawStart;
        this.size = drawCount;
        this.maxElementCount = maxElementCount;
    }

    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        throw new UnsupportedOperationException("GPU-culled batches build their commands on the GPU");
    }

    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        throw new UnsupportedOperationException("GPU-culled batches build their commands on the GPU");
    }

    @Override
    public void upload(CommandList commandList) {
    }

    @Override
    public void delete() {
    }

    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        this.culler.drawRegionRange(commandList, tessellation, primitiveType, this.drawStart, this.size);
    }
}
