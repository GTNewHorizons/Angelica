package com.seibel.distanthorizons.interfaces;

import net.minecraft.client.renderer.texture.DynamicTexture;

public interface IMixinEntityRenderer {
    DynamicTexture getLightmapTexture();
}
