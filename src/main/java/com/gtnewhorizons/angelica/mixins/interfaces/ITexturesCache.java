package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.util.IIcon;

import java.util.Set;

public interface ITexturesCache {

    Set<IIcon> getRenderedTextures();
    void enableTextureTracking();
}
