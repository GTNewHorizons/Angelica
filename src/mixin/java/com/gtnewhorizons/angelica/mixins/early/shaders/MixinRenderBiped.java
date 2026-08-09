package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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

    /**
     * Set item ID when rendering armor on biped entities.
     * Wraps setRenderPassModel() which is called inside shouldRenderPass().
     */
    @WrapOperation(
        method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderBiped;setRenderPassModel(Lnet/minecraft/client/model/ModelBase;)V")
    )
    private void iris$setArmorItemId(RenderBiped instance, ModelBase model, Operation<Void> original, EntityLiving entity, int armorSlot, float partialTicks) {
        // Get the armor item stack for this slot (slot 0=boots, 1=legs, 2=chest, 3=helmet)
        ItemStack itemStack = entity.func_130225_q(3 - armorSlot);
        ItemIdManager.setItemId(itemStack);

        original.call(instance, model);
    }
}
