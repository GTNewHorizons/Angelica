package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * Exposes a method to the API that allow mods to add texture update compat
 */
public class TextureServices {
    public static void updateBlockTextureAnimation(IIcon icon, IBlockAccess blockAccess){
        AnimationsRenderUtils.markBlockTextureForUpdate(icon);
    }
    public static void updateTextureAnimation(IIcon icon){
        AnimationsRenderUtils.markBlockTextureForUpdate(icon);
    }
}
