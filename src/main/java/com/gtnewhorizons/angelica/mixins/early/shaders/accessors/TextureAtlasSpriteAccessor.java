package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteAccessor {
    @Accessor("animationMetadata")
    AnimationMetadataSection getMetadata();

    @Accessor("framesTextureData")
    List<int[][]> getFramesTextureData();

    @Accessor("frameCounter")
    int getFrame();

    @Accessor("frameCounter")
    void setFrame(int frame);

    @Accessor("tickCounter")
    int getSubFrame();

    @Accessor("tickCounter")
    void setSubFrame(int subFrame);
}
