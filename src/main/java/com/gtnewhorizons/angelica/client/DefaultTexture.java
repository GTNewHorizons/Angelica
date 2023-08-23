package com.gtnewhorizons.angelica.client;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;

public class DefaultTexture extends AbstractTexture {

    public DefaultTexture() {
        loadTexture(null);
    }

    public void loadTexture(IResourceManager resourcemanager) {
        int[] aint = ShadersTex.createAIntImage(1, 0xFFFFFFFF);
        ShadersTex.setupTexture(this.angelica$getMultiTexID(), aint, 1, 1, false, false);
    }
}
