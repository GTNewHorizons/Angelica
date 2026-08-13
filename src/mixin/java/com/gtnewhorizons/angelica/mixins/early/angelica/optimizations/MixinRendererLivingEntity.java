package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import com.gtnewhorizons.angelica.helpers.RendererLivingEntityHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {

    @Redirect(
        method = "rotateCorpse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;getCommandSenderName()Ljava/lang/String;"),
        require = 0)
    private static String angelica$skipUnusedEntityName(EntityLivingBase entity) {
        return RendererLivingEntityHelper.getUpsideDownName(entity);
    }

    @Redirect(
        method = "rotateCorpse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/EnumChatFormatting;getTextWithoutFormattingCodes(Ljava/lang/String;)Ljava/lang/String;"),
        require = 0)
    private static String angelica$skipUnusedNameFormatting(String entityName) {
        return RendererLivingEntityHelper.stripFormattingCodes(entityName);
    }
}
