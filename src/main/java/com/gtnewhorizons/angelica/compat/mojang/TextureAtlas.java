package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITickable;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

public abstract class TextureAtlas extends AbstractTexture implements ITickable, IResourceManagerReloadListener {

    @Override
    public void tick() {

    }

    @Override
    public void onResourceManagerReload(IResourceManager p_110549_1_) {

    }
}
