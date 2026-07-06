package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.utils.Mipmaps;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TextureUtil.class)
public class MixinTextureUtil_OptimizeMipmap {

    /**
     * @author SuperCoder79
     * @reason Rewrite mipmap color math to use memoized value array instead of using Math.pow directly
     */
    @Overwrite
    private static int func_147943_a(int one, int two, int three, int four, boolean alpha) {
        if (!alpha) {
            final int a = Mipmaps.getColorComponent(one, two, three, four, 24);
            final int r = Mipmaps.getColorComponent(one, two, three, four, 16);
            final int g = Mipmaps.getColorComponent(one, two, three, four, 8);
            final int b = Mipmaps.getColorComponent(one, two, three, four, 0);
            return a << 24 | r << 16 | g << 8 | b;
        } else {
            float a = 0.0F;
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            if (one >> 24 != 0) {
                a += Mipmaps.get(one >> 24);
                r += Mipmaps.get(one >> 16);
                g += Mipmaps.get(one >> 8);
                b += Mipmaps.get(one >> 0);
            }

            if (two >> 24 != 0) {
                a += Mipmaps.get(two >> 24);
                r += Mipmaps.get(two >> 16);
                g += Mipmaps.get(two >> 8);
                b += Mipmaps.get(two >> 0);
            }

            if (three >> 24 != 0) {
                a += Mipmaps.get(three >> 24);
                r += Mipmaps.get(three >> 16);
                g += Mipmaps.get(three >> 8);
                b += Mipmaps.get(three >> 0);
            }

            if (four >> 24 != 0) {
                a += Mipmaps.get(four >> 24);
                r += Mipmaps.get(four >> 16);
                g += Mipmaps.get(four >> 8);
                b += Mipmaps.get(four >> 0);
            }

            a /= 4.0F;
            r /= 4.0F;
            g /= 4.0F;
            b /= 4.0F;
            int ia = (int) (Math.pow((double) a, 0.45454545454545453) * 255.0);
            final int ir = (int) (Math.pow((double) r, 0.45454545454545453) * 255.0);
            final int ig = (int) (Math.pow((double) g, 0.45454545454545453) * 255.0);
            final int ib = (int) (Math.pow((double) b, 0.45454545454545453) * 255.0);
            if (ia < 96) {
                ia = 0;
            }

            return ia << 24 | ir << 16 | ig << 8 | ib;
        }
    }
}
