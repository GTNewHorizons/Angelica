package com.prupe.mcpatcher.ctm;

import com.prupe.mcpatcher.mal.tile.TileLoader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;

@SideOnly(Side.CLIENT)
public class CTMAtlasSprite extends TextureAtlasSprite {

    private final TileLoader loader;
    private final boolean aniFiltering;

    public CTMAtlasSprite(TileLoader loader, boolean aniFiltering, String name) {
        super(name);
        this.loader = loader;
        this.aniFiltering = aniFiltering;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        BufferedImage img = loader.getImageForPath(location);
        if(img == null) return true;
        loadSprite(new BufferedImage[]{img, null, null, null, null}, null, aniFiltering);
        return false;
    }
}
