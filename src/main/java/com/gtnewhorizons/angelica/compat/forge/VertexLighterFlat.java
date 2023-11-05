package com.gtnewhorizons.angelica.compat.forge;

import com.gtnewhorizons.angelica.compat.mojang.BlockColors;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;

public class VertexLighterFlat {

    public VertexLighterFlat(BlockColors colors) {}

    public void setParent(VertexBufferConsumer consumer) {}

    public void setTransform(MatrixStack.Entry peek) {}

    public void setWorld(BlockRenderView world) {}

    public void setState(BlockState state) {}

    public void setBlockPos(BlockPos pos) {}

    public void updateBlockInfo() {}

    public void resetBlockInfo() {}
}
