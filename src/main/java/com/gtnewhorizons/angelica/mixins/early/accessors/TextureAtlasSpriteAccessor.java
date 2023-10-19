package com.gtnewhorizons.angelica.mixins.early.accessors;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteAccessor {
    @Accessor("animationMetadata")
    AnimationMetadataSection getMetadata();

//    @Accessor("mainImage")
//    NativeImage[] getMainImage();
//
//    @Accessor("x")
//    int getX();
//
//    @Accessor("y")
//    int getY();

    @Accessor("frameCounter")
    int getFrame();

    @Accessor("frameCounter")
    void setFrame(int frame);

    @Accessor("tickCounter")
    int getSubFrame();

    @Accessor("tickCounter")
    void setSubFrame(int subFrame);


    //
//    @Invoker("upload")
//    void callUpload(int frameIndex);
}
