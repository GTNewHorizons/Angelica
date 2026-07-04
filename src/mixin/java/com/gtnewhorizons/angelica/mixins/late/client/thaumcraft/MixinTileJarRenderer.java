package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.Tc4JarMeshes;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.client.renderers.tile.TileJarRenderer;
import thaumcraft.common.tiles.TileJarFillable;

@Mixin(value = TileJarRenderer.class, remap = false)
public abstract class MixinTileJarRenderer {

    @Shadow(remap = false)
    abstract void renderLiquid(TileJarFillable te, double x, double y, double z, float f);

    @Redirect(method = "renderTileEntityAt(Lthaumcraft/common/tiles/TileJar;DDDF)V", at = @At(value = "INVOKE", target = "Lthaumcraft/client/renderers/tile/TileJarRenderer;renderLiquid(Lthaumcraft/common/tiles/TileJarFillable;DDDF)V"))
    private void angelica$renderLiquidCached(TileJarRenderer self, TileJarFillable te, double x, double y, double z, float f) {
        if (!Tc4JarMeshes.renderLiquid(te)) {
            this.renderLiquid(te, x, y, z, f);
        }
    }

    @Redirect(method = "renderLiquid", at = @At(value = "NEW", target = "net/minecraft/client/renderer/RenderBlocks"))
    private RenderBlocks angelica$reuseRenderBlocks() {
        return Tc4JarMeshes.fallbackRenderBlocks();
    }

    @Redirect(method = "renderTileEntityAt(Lthaumcraft/common/tiles/TileJar;DDDF)V", at = @At(value = "INVOKE", target = "Lthaumcraft/client/lib/UtilsFX;renderQuadCenteredFromTexture(Ljava/lang/String;FFFFIIF)V"))
    private void angelica$cachedLabelBacking(String texture, float scale, float red, float green, float blue, int brightness, int blend, float opacity) {
        if ("textures/models/label.png".equals(texture) && scale == 0.5f && blend == 771 && opacity == 1.0f) {
            Tc4JarMeshes.renderLabelBacking();
        } else {
            UtilsFX.renderQuadCenteredFromTexture(texture, scale, red, green, blue, brightness, blend, opacity);
        }
    }

    @Redirect(method = "renderTileEntityAt(Lthaumcraft/common/tiles/TileJar;DDDF)V", at = @At(value = "INVOKE", target = "Lthaumcraft/client/lib/UtilsFX;drawTag(IILthaumcraft/api/aspects/Aspect;)V"))
    private void angelica$cachedLabelTag(int x, int y, Aspect aspect) {
        if (x == -8 && y == -8 && aspect != null) {
            Tc4JarMeshes.renderLabelTag(aspect);
        } else {
            UtilsFX.drawTag(x, y, aspect);
        }
    }
}
