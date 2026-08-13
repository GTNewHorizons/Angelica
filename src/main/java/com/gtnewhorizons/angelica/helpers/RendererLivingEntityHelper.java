package com.gtnewhorizons.angelica.helpers;

import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.FORMATTING_CHAR;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

public final class RendererLivingEntityHelper {

    private RendererLivingEntityHelper() {}

    public static String getUpsideDownName(EntityLivingBase entity) {
        if (entity instanceof EntityLiving living) {
            return living.hasCustomNameTag() ? entity.getCommandSenderName() : "";
        }

        if (entity instanceof EntityPlayer player) {
            return player.getHideCape() ? "" : entity.getCommandSenderName();
        }

        return entity.getCommandSenderName();
    }

    public static String stripFormattingCodes(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return "";
        }

        return entityName.indexOf(FORMATTING_CHAR) < 0
            ? entityName
            : EnumChatFormatting.getTextWithoutFormattingCodes(entityName);
    }
}
