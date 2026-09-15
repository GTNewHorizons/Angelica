package com.gtnewhorizons.angelica.debug.flyby;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;

public final class FlybyFallGuard {
    public static final FlybyFallGuard INSTANCE = new FlybyFallGuard();

    private FlybyFallGuard() {}

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (FlybyRunner.sceneGuarded() && !(event.entityLiving instanceof EntityPlayer)) {
            event.setCanceled(true);
        }
    }
}
