package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to set currentRenderedItem ID when rendering armor and equipment on biped entities.
 * RENDER ORDER:
 * 1. doRender() starts (parent resets item ID to 0)
 * 2. Main entity body
 * 3. Armor loop (4 slots) - we set ID per armor piece
 * 4. renderEquippedItems(): - Handled via ItemRenderer
 * - Held item
 */
@Mixin(RenderBiped.class)
public class MixinRenderBiped {

    @Inject(
        method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderBiped;setRenderPassModel(Lnet/minecraft/client/model/ModelBase;)V")
    )
    private void iris$setArmorItemId(EntityLiving entity, int armorSlot, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        ItemIdManager.setItemId(entity.func_130225_q(3 - armorSlot));
    }
}
