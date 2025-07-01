package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu {
    private static boolean angelica$hasFirstInit;

    @Inject(method = "initGui", at = @At("RETURN"))
    public void angelica$shadersOnLoadingComplete(CallbackInfo ci) {
        if(!angelica$hasFirstInit && GLStateManager.isMainThread()) {
            angelica$hasFirstInit = true;
            Iris.onLoadingComplete();
        }
    }

}
