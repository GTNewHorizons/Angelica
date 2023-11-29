package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiIngameForge.class)
public interface GuiIngameForgeAccessor {

    @Invoker(remap = false)
    void callRenderCrosshairs(int width, int height);
}
