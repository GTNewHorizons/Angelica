package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.GuiModList;
import net.minecraft.client.gui.GuiIngameMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FMLClientHandler.class)
public abstract class MixinFMLClientHandler {
    @Shadow(remap = false) public abstract void showGuiScreen(Object clientGuiElement);

    @Inject(method = "showInGameModOptions", at = @At("HEAD"), cancellable = true, remap = false)
    private void showModsList(GuiIngameMenu guiIngameMenu, CallbackInfo ci) {
        showGuiScreen(new GuiModList(guiIngameMenu));
        ci.cancel();
    }
}
