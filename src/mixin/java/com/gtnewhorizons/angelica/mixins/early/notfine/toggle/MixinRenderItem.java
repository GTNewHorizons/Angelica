package com.gtnewhorizons.angelica.mixins.early.notfine.toggle;

import jss.notfine.core.SettingsManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderItem.class, priority = 1100)
abstract public class MixinRenderItem {

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z",
            opcode = Opcodes.GETFIELD
        ),
        allow = 1
    )
    private boolean notFine$toggleDroppedItemDetail(GameSettings settings) {
        return SettingsManager.droppedItemDetail;
    }

}
