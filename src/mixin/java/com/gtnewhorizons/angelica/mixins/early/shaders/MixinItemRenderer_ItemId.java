package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.mixins.interfaces.ItemRendererAccessor;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item ID and glint handling for items rendered on entities.
 */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer_ItemId implements ItemRendererAccessor {

    @Shadow private ItemStack itemToRender;

    @Override
    public ItemStack angelica$getItemToRender() {
        return itemToRender;
    }

    @Surround(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        remap = false
    )
    private void iris$entityItemId(EntityLivingBase entity, ItemStack itemStack, int renderPass, IItemRenderer.ItemRenderType type) {
        final boolean translucent = HandRenderer.INSTANCE.isItemTranslucent(itemStack);

        @Surround.Carry
        final int stateDepth = ItemIdManager.beginCutout(itemStack);

        @Surround.Carry
        final Boolean prevTranslucency = GbufferPrograms.beginTranslucencyDeclaration(translucent);
    }

    @Surround.Finally
    private void iris$entityItemRestore(@Surround.Carry Boolean prevTranslucency, @Surround.Carry int stateDepth) {
        GbufferPrograms.endTranslucencyDeclaration(prevTranslucency);
        ItemIdManager.endCutout(stateDepth);
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$glintStart(CallbackInfo ci) {
        ItemIdManager.pushItemId();
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        ShaderGlint.beginGlint();
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$glintEnd(CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
        ShaderGlint.endGlint();
        ItemIdManager.popItemId();
    }
}
