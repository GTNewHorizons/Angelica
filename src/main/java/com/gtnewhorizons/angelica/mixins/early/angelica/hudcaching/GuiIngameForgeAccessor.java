package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiIngameForge.class)
public interface GuiIngameForgeAccessor {

    @Invoker(remap = false)
    void callRenderCrosshairs(int width, int height);
    
    @Invoker(remap = false)
    void callRenderHelmet(ScaledResolution res, float partialTicks, boolean hasScreen, int mouseX, int mouseY);
    
    @Invoker(remap = false)
    void callRenderPortal(int width, int height, float partialTicks);
}
