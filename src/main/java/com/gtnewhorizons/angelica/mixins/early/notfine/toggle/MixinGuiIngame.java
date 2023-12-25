package com.gtnewhorizons.angelica.mixins.early.notfine.toggle;

import jss.notfine.core.SettingsManager;
import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {GuiIngame.class, GuiIngameForge.class})
public abstract class MixinGuiIngame {

    @Redirect(
        method = "renderGameOverlay(FZII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;isFancyGraphicsEnabled()Z"
        )
    )
    private boolean notFine$toggleVignette(float whyAndHowIsThisAFloat) {
        return SettingsManager.vignette;
    }

}
