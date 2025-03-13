package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.entity.player.EntityPlayer;

public interface EntityRendererAccessor {
    float invokeGetNightVisionBrightness(EntityPlayer entityPlayer, float partialTicks);
}
