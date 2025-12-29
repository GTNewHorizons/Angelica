package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface TextureMapExtension {
    TextureAtlasSprite celeritas$findFromUV(float u, float v);
}
