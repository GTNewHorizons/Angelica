package com.gtnewhorizons.angelica.client.textures;

import net.minecraft.client.resources.data.AnimationMetadataSection;

public interface ISpriteExt {

    boolean isAnimation();

    int getFrame();

    void callUpload(int frameIndex);

    AnimationMetadataSection getMetadata();
}
