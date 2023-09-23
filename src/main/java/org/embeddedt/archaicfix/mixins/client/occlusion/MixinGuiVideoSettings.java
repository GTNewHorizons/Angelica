package org.embeddedt.archaicfix.mixins.client.occlusion;

import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiVideoSettings.class)
public class MixinGuiVideoSettings {
    @Redirect(method = "initGui", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/OpenGlHelper;field_153197_d:Z"))
    private boolean neverUseAdvancedGl() {
        return false;
    }
}
