package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;

public abstract class ChunkGraphicsState {
    private final int x, y, z;

    private ByteBuffer translucencyData;

    protected ChunkGraphicsState(ChunkRenderContainer<?> container) {
        this.x = container.getRenderX();
        this.y = container.getRenderY();
        this.z = container.getRenderZ();
    }

    public abstract void delete(CommandList commandList);

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public ByteBuffer getTranslucencyData() {
        return this.translucencyData;
    }

    public void setTranslucencyData(ByteBuffer data) {
        this.translucencyData = data;
    }
}
