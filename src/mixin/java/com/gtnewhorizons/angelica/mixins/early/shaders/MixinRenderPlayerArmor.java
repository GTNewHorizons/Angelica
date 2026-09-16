package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Mixin to set currentRenderedItem ID when rendering armor and equipment on players.
 * RENDER ORDER (same as parent RendererLivingEntity):
 * 1. doRender() starts (parent resets item ID to 0)
 * 2. Main player body
 * 3. Armor loop (4 slots) - we set ID per armor piece
 * 4. renderEquippedItems(): - Handled via ItemRenderer
 * - Helmet (Pumpkin head or other items)
 * - Deadmau5 ears
 * - Cape - sets "minecraft:player_cape" ID here
 * - Held item
 */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayerArmor {
    @Unique
    private static final NamespacedId PLAYER_CAPE = new NamespacedId("minecraft", "player_cape");

    @Inject(
        method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderPlayer;setRenderPassModel(Lnet/minecraft/client/model/ModelBase;)V")
    )
    private void iris$setArmorItemId(AbstractClientPlayer player, int armorSlot, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        ItemIdManager.setItemId(player.getCurrentArmor(3 - armorSlot));
    }

    /**
     * Set "minecraft:player_cape" when rendering the player's cape, and declare the cape opaque.
     */
    @WrapOperation(
        method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBiped;renderCloak(F)V")
    )
    private void iris$setCapeItemId(ModelBiped modelBiped, float scale, Operation<Void> original) {
        if (BlockRenderingSettings.INSTANCE.getItemIds() != null) {
            assert BlockRenderingSettings.INSTANCE.getItemIds() != null;
            int capeId = Objects.requireNonNull(BlockRenderingSettings.INSTANCE.getItemIds()).applyAsInt(PLAYER_CAPE);
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(capeId);
        }

        final Boolean previous = GbufferPrograms.beginTranslucencyDeclaration(Boolean.FALSE);
        try {
            original.call(modelBiped, scale);
        } finally {
            GbufferPrograms.endTranslucencyDeclaration(previous);
        }
        ItemIdManager.resetItemId();
    }
}
