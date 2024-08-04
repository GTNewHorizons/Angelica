package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import com.gtnewhorizons.angelica.mixins.interfaces.TextureAtlasSpriteAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements TextureAtlasSpriteAccessor {
    @Accessor("animationMetadata")
    public abstract AnimationMetadataSection getMetadata();

    @Accessor("framesTextureData")
    public abstract  List<int[][]> getFramesTextureData();

    @Accessor("frameCounter")
    public abstract int getFrame();

    @Accessor("frameCounter")
    public abstract void setFrame(int frame);

    @Accessor("tickCounter")
    public abstract int getSubFrame();

    @Accessor("tickCounter")
    public abstract void setSubFrame(int subFrame);
}
