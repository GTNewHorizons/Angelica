package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameSettings.class)
public class MixinGameSettings_VSync {

    @Redirect(method = "setOptionValue", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setVSyncEnabled(Z)V", remap = false))
    private void umbra$redirectVSync(boolean sync) {
        GLStateManager.setVSyncEnabled(sync);
    }
}
