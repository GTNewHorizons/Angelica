package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(
            at = @At(
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;",
                    value = "FIELD"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION,
            method = "updateEquippedItem()V")
    private void angelica$setItemToRender(CallbackInfo ci, EntityClientPlayerMP entityclientplayermp,
            ItemStack itemstack) {
        Shaders.itemToRender = itemstack;
    }

    @ModifyArg(
            at = @At(
                    ordinal = 0,
                    remap = true,
                    target = "Lnet/minecraft/client/renderer/OpenGlHelper;glBlendFunc(IIII)V",
                    value = "INVOKE"),
            index = 3,
            method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
            remap = false)
    private int angelica$adjust_dfactorAlpha(int original) {
        return 1;
    }

}
