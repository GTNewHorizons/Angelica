package com.prupe.mcpatcher.ctm;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.prupe.mcpatcher.mal.tile.TileLoader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jss.notfine.render.InterpolatedIcon;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;

@SideOnly(Side.CLIENT)
public class GeneratedCTMAtlasSprite extends InterpolatedIcon {

    private final TileLoader loader;
    private final boolean aniFiltering;
    private boolean interpolated;

    public GeneratedCTMAtlasSprite(TileLoader loader, boolean aniFiltering, String name) {
        super(name);
        this.loader = loader;
        this.aniFiltering = aniFiltering;
    }

    @Override
    protected void updateAnimationInterpolated() throws IllegalArgumentException, IllegalAccessException {
        if(interpolated) super.updateAnimationInterpolated();
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        BufferedImage img = loader.getImageForPath(location);
        if(img == null) return true;
        try {
            ResourceLocation metaLoc = getMetaLoc(location);
            IResource iresource = manager.getResource(metaLoc);
            AnimationMetadataSection anim = (AnimationMetadataSection)iresource.getMetadata("animation");
            interpolated = anim != null && AngelicaConfig.enableNotFineFeatures && shouldInterpolate(iresource);
            loadSprite(new BufferedImage[]{img, null, null, null, null}, anim, aniFiltering);
        }catch (Exception exception){
            CTMUtils.logger.error("Error loading sprite for " + location);
            exception.printStackTrace();
            return true;
        }
        return false;
    }

    private static boolean shouldInterpolate(IResource resource){
        try {
            if (resource instanceof SimpleResource simple) {
                return simple.mcmetaJson.getAsJsonObject("animation")
                    .getAsJsonPrimitive("interpolate").getAsBoolean();
            }
        }catch (Exception _){}
        return false;
    }

    /**
     * Find the mcmeta file associated with 0.png
     */
    private static ResourceLocation getMetaLoc(ResourceLocation location) {
        String path = location.getResourcePath();
        int index = path.lastIndexOf('/');
        if(index == -1) return new ResourceLocation(location.getResourceDomain(), "0.png");
        path = path.substring(0, index) + "/0.png";
        return new ResourceLocation(location.getResourceDomain(), path);
    }
}
