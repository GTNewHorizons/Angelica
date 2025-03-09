package org.taumc.celeritas.impl.render.terrain.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.taumc.celeritas.impl.extensions.SpriteExtension;

public class SpriteUtil {
    public static void markSpriteActive(TextureAtlasSprite sprite) {
        ((SpriteExtension)sprite).celeritas$markActive();
    }
}
