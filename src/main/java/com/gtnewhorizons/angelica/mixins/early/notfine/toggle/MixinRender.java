package com.gtnewhorizons.angelica.mixins.early.notfine.toggle;

import jss.notfine.core.SettingsManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Render.class)
public abstract class MixinRender {

    @Redirect(
        method = "doRenderShadowAndFire",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private boolean notFine$toggleEntityShadows(GameSettings settings) {
        return SettingsManager.shadows;
    }

}
