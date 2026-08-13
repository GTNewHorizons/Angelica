package com.gtnewhorizons.angelica.mixins.late.client.starminer;

import com.gtnewhorizons.angelica.helpers.RendererLivingEntityHelper;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "jp.mc.ancientred.starminer.core.TransformClientHelper", remap = false)
public class MixinTransformClientHelper {

    @Redirect(
        method = "rotateCorpseByGravity(Lnet/minecraft/entity/EntityLivingBase;FFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;getCommandSenderName()Ljava/lang/String;",
            remap = true),
        require = 0)
    private static String angelica$skipUnusedEntityName(EntityLivingBase entity) {
        return RendererLivingEntityHelper.getUpsideDownName(entity);
    }

    @Redirect(
        method = "rotateCorpseByGravity(Lnet/minecraft/entity/EntityLivingBase;FFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/EnumChatFormatting;getTextWithoutFormattingCodes(Ljava/lang/String;)Ljava/lang/String;",
            remap = true),
        require = 0)
    private static String angelica$skipUnusedNameFormatting(String entityName) {
        return RendererLivingEntityHelper.stripFormattingCodes(entityName);
    }
}
