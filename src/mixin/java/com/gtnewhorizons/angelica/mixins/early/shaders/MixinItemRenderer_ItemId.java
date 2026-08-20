package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.mixins.interfaces.ItemRendererAccessor;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    @WrapMethod(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        remap = false
    )
    private void iris$entityItemId(EntityLivingBase entity, ItemStack itemStack, int renderPass, IItemRenderer.ItemRenderType type, Operation<Void> original) {
        final int prevItemId = ItemIdManager.getItemId();
        final long prevCutout = GbufferPrograms.pushCutoutDefaults();

        final Boolean prevTranslucency = GbufferPrograms.beginTranslucencyDeclaration(
            HandRenderer.INSTANCE.isItemTranslucent(itemStack));

        ItemIdManager.setItemId(itemStack);
        try {
            original.call(entity, itemStack, renderPass, type);
        } finally {
            GbufferPrograms.endTranslucencyDeclaration(prevTranslucency);
            ItemIdManager.setItemIdRaw(prevItemId);
            GbufferPrograms.popCutoutDefaults(prevCutout);
        }
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$glintStart(CallbackInfo ci) {
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
    }
}
