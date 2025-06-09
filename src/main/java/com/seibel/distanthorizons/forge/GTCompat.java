package com.seibel.distanthorizons.forge;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.common.render.GTRenderedTexture;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import gregtech.api.interfaces.IBlockWithTextures;

import java.lang.reflect.Field;

public class GTCompat {

    private Object getObjectByReflection(Object base, String name) {
        try {
            Field field = base.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(base);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public IIcon resolveIcon(Block block, int meta) {
        if (block instanceof IBlockWithTextures blockWithTextures) {
            ITexture[][] textures = blockWithTextures.getTextures(meta);
            if (textures != null && textures[0] != null) {
                ITexture tex0 = textures[0][0];
                if (tex0 instanceof GTRenderedTexture renderedTexture) {
                    IIconContainer container = (IIconContainer) getObjectByReflection(tex0, "mIconContainer");
                    if (container != null) {
                        return container.getIcon();
                    }
                }
            }
        }
        return null;
    }
}
