package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

public interface TextureAtlasSpriteAccessor {
    AnimationMetadataSection getMetadata();

    List<int[][]> getFramesTextureData();

    int getFrame();

    void setFrame(int frame);

    int getSubFrame();

    void setSubFrame(int subFrame);

}
