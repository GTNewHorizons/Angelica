package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.GameSettings;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public class MixinRenderItem {
    @Redirect(method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z"))
    private boolean forceFancyGraphics(GameSettings settings) {
        if(ArchaicConfig.forceFancyItems)
            return true;
        else
            return settings.fancyGraphics;
    }
}
