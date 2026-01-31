package com.gtnewhorizons.angelica.mixins.late.client.etfuturum;

import ganymedes01.etfuturum.client.renderer.entity.elytra.LayerBetterElytra;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Sets a custom material ID for elytra when rendered with a cape texture.
 * This allows shader developers to target elytra with capes using "minecraft:elytra_with_cape"
 * in their item.properties files.
 */
@Mixin(value = LayerBetterElytra.class, remap = false)
public class MixinLayerBetterElytra {

    @Unique
    private static final NamespacedId ELYTRA_WITH_CAPE = new NamespacedId("minecraft", "elytra_with_cape");
    @Unique
    private static final NamespacedId ELYTRA = new NamespacedId("etfuturum", "elytra");
    @Unique
    private static boolean iris$didSetId = false;

    /**
     * Before rendering the elytra model, set the appropriate material ID based on whether
     * the player has a cape or not. This allows shader developers to target both cases.
     */
    @Inject(
        method = "doRenderLayer",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V", ordinal = 0, shift = At.Shift.BEFORE),
        remap = false
    )
    private static void iris$setElytraId(EntityLivingBase entityIn, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float scale, CallbackInfo ci) {
        iris$didSetId = false;

        if (entityIn instanceof AbstractClientPlayer player) {
            if (BlockRenderingSettings.INSTANCE.getItemIds() != null) {
                // Check if the player has a cape and set appropriate ID
                NamespacedId elytraId = player.func_152122_n() ? ELYTRA_WITH_CAPE : ELYTRA;
                int materialId = Objects.requireNonNull(BlockRenderingSettings.INSTANCE.getItemIds()).applyAsInt(elytraId);
                CapturedRenderingState.INSTANCE.setCurrentRenderedItem(materialId);
                iris$didSetId = true;
            }
        }
    }

    /**
     * After rendering the elytra, reset the material ID only if we set it.
     */
    @Inject(
        method = "doRenderLayer",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopAttrib()V", ordinal = 0, shift = At.Shift.AFTER),
        remap = false
    )
    private static void iris$resetElytraId(EntityLivingBase entityIn, float limbSwing, float limbSwingAmount,
                                            float partialTicks, float ageInTicks, float scale, CallbackInfo ci) {
        // Reset only if we set it
        if (iris$didSetId) {
            ItemIdManager.resetItemId();
        }
    }
}
