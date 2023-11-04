package me.jellysquid.mods.sodium.client.gl.buffer;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;

public abstract class GlBuffer extends GlObject {
    public static final int NULL_BUFFER_ID = 0;

    protected final GlBufferUsage usage;

    protected GlBuffer(RenderDevice owner, GlBufferUsage usage) {
        super(owner);

        this.setHandle(GlStateManager.genBuffers());

        this.usage = usage;
    }

    public GlBufferUsage getUsageHint() {
        return this.usage;
    }
}
