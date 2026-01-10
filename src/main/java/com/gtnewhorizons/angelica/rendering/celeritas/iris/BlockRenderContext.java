package com.gtnewhorizons.angelica.rendering.celeritas.iris;

public class BlockRenderContext {
    public int localPosX, localPosY, localPosZ;
    public short blockId = -1;
    public short renderType = -1;
    public byte lightValue;

    public void set(int localX, int localY, int localZ, short blockId, short renderType, byte lightValue) {
        this.localPosX = localX;
        this.localPosY = localY;
        this.localPosZ = localZ;
        this.blockId = blockId;
        this.renderType = renderType;
        this.lightValue = lightValue;
    }

    public void reset() {
        blockId = -1;
        renderType = -1;
        lightValue = 0;
    }
}
