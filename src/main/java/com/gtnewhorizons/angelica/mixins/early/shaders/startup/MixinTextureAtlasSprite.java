package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import net.coderbot.iris.texture.pbr.PBRSpriteHolder;
import net.coderbot.iris.texture.pbr.TextureAtlasSpriteExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite implements TextureAtlasSpriteExtension {
    @Unique
    private PBRSpriteHolder pbrHolder;

    @Override
    public PBRSpriteHolder getPBRHolder() {
        return pbrHolder;
    }

    @Override
    public PBRSpriteHolder getOrCreatePBRHolder() {
        if (pbrHolder == null) {
            pbrHolder = new PBRSpriteHolder();
        }
        return pbrHolder;
    }
}
