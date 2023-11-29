package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityRenderer.class)
public class MixinEntityRenderer_HUDCaching {

    @Redirect(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(FZII)V"))
    public void angelica$renderCachedHUD(GuiIngame guiIngame, float partialTicks, boolean p_73830_2_, int p_73830_3_,
            int p_73830_4_) {
        HUDCaching.renderCachedHud(
                (EntityRenderer) (Object) this,
                guiIngame,
                partialTicks,
                p_73830_2_,
                p_73830_3_,
                p_73830_4_);
    }
}
