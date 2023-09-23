package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiButton.class)
public class MixinGuiButton {
    /**
     * Make buttons render without yellow text (like in 1.14).
     */
    @Redirect(method = "drawButton", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/gui/GuiButton;field_146123_n:Z", ordinal = 1))
    private boolean isHovered(GuiButton button) {
        if(!ArchaicConfig.enableNewButtonAppearance || button instanceof GuiOptionSlider)
            return button.field_146123_n;
        else
            return false;
    }
}
