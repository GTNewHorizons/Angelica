package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks_CrossedSquaresNormal {

    @Shadow public IIcon overrideBlockTexture;
    @Shadow public abstract boolean hasOverrideBlockTexture();

    /**
     * Vanilla sets a single (0, -1, 0) normal for all crossed-square vertices in renderBlockAsItem.
     * This downward-pointing normal causes shader packs to compute incorrect shadow bias (pushing the
     * shadow sample into the parent entity's body, e.g. mooshroom and anything that extends mooshrooms)
     * and zero diffuse sun lighting.
     *
     * This mixin replaces the singular normal with per-quad face normals perpendicular to each diagonal plane.
     * This gives each face correct directional lighting and shadow bias that pushes samples away from the face rather
     * than into the entity body.
     */
    @Redirect(
        method = "renderBlockAsItem(Lnet/minecraft/block/Block;IF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;drawCrossedSquares(Lnet/minecraft/util/IIcon;DDDF)V")
    )
    private void angelica$drawCrossedSquaresWithPerFaceNormals(RenderBlocks instance, IIcon icon, double x, double y, double z, float scale) {
        if (this.hasOverrideBlockTexture()) {
            icon = this.overrideBlockTexture;
        }

        final Tessellator tess = Tessellator.instance;
        final double minU = icon.getMinU();
        final double minV = icon.getMinV();
        final double maxU = icon.getMaxU();
        final double maxV = icon.getMaxV();

        final double d7 = 0.45D * scale;
        final double x0 = x + 0.5D - d7;
        final double x1 = x + 0.5D + d7;
        final double z0 = z + 0.5D - d7;
        final double z1 = z + 0.5D + d7;
        final double yTop = y + (double) scale;

        final float inv_sqrt_2 = (float) (1.0 / Math.sqrt(2.0));

        tess.setNormal(inv_sqrt_2, 0.0F, -inv_sqrt_2);
        tess.addVertexWithUV(x0, yTop, z0, minU, minV);
        tess.addVertexWithUV(x0, y,    z0, minU, maxV);
        tess.addVertexWithUV(x1, y,    z1, maxU, maxV);
        tess.addVertexWithUV(x1, yTop, z1, maxU, minV);

        tess.setNormal(-inv_sqrt_2, 0.0F, inv_sqrt_2);
        tess.addVertexWithUV(x1, yTop, z1, minU, minV);
        tess.addVertexWithUV(x1, y,    z1, minU, maxV);
        tess.addVertexWithUV(x0, y,    z0, maxU, maxV);
        tess.addVertexWithUV(x0, yTop, z0, maxU, minV);

        tess.setNormal(-inv_sqrt_2, 0.0F, -inv_sqrt_2);
        tess.addVertexWithUV(x0, yTop, z1, minU, minV);
        tess.addVertexWithUV(x0, y,    z1, minU, maxV);
        tess.addVertexWithUV(x1, y,    z0, maxU, maxV);
        tess.addVertexWithUV(x1, yTop, z0, maxU, minV);

        tess.setNormal(inv_sqrt_2, 0.0F, inv_sqrt_2);
        tess.addVertexWithUV(x1, yTop, z0, minU, minV);
        tess.addVertexWithUV(x1, y,    z0, minU, maxV);
        tess.addVertexWithUV(x0, y,    z1, maxU, maxV);
        tess.addVertexWithUV(x0, yTop, z1, maxU, minV);
    }
}
