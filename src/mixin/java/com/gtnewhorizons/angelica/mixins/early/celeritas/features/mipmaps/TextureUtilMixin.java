package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import static org.embeddedt.embeddium.impl.texture.MipmapHelper.weightedAverageColor;

@Mixin(TextureUtil.class)
public class TextureUtilMixin {
    /**
     * @author coderbot
     * @reason replace the vanilla blending function with our improved function
     */
    @Overwrite
    private static int func_147943_a(int one, int two, int three, int four, boolean checkAlpha) {
        // First blend horizontally, then blend vertically.
        //
        // This works well for the case where our change is the most impactful (grass side overlays)
        return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
    }
}
