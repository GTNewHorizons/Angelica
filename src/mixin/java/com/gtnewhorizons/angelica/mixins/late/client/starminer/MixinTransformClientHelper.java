package com.gtnewhorizons.angelica.mixins.late.client.starminer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "jp.mc.ancientred.starminer.core.TransformClientHelper", remap = false)
public abstract class MixinTransformClientHelper {

    @WrapOperation(
        method = "rotateCorpseByGravity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;getCommandSenderName()Ljava/lang/String;"),
        remap = true,
        require = 0)
    private static String angelica$skipUnusedEntityName(EntityLivingBase entity, Operation<String> original) {
        if ((entity instanceof EntityLiving living && living.hasCustomNameTag())
            || (entity instanceof EntityPlayer player && !player.getHideCape())) {
            return original.call(entity);
        }

        return null;
    }

    @WrapOperation(
        method = "rotateCorpseByGravity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/EnumChatFormatting;getTextWithoutFormattingCodes(Ljava/lang/String;)Ljava/lang/String;"),
        remap = true,
        require = 0)
    private static String angelica$skipUnusedNameFormatting(String entityName, Operation<String> original) {
        return entityName == null ? "" : original.call(entityName);
    }
}
