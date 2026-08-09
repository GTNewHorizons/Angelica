package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {

    @Redirect(
        method = "rotateCorpse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;getCommandSenderName()Ljava/lang/String;"),
        require = 0)
    private static String angelica$skipUnusedEntityName(EntityLivingBase entity) {
        if ((entity instanceof EntityLiving living && living.hasCustomNameTag())
            || (entity instanceof EntityPlayer player && !player.getHideCape())) {
            return entity.getCommandSenderName();
        }

        return null;
    }

    @Redirect(
        method = "rotateCorpse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/EnumChatFormatting;getTextWithoutFormattingCodes(Ljava/lang/String;)Ljava/lang/String;"),
        require = 0)
    private static String angelica$skipUnusedNameFormatting(String entityName) {
        return entityName == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(entityName);
    }
}
