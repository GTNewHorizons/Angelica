package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameForgeAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.GuiIngameForge;

@Mixin(GuiIngameForge.class)
public abstract class MixinGuiIngameForge implements GuiIngameForgeAccessor {

    @Invoker(remap = false)
    public abstract void callRenderCrosshairs(int width, int height);

    @Invoker(remap = false)
    public abstract void callRenderHelmet(ScaledResolution res, float partialTicks, boolean hasScreen, int mouseX, int mouseY);

    @Invoker(remap = false)
    public abstract void callRenderPortal(int width, int height, float partialTicks);

    @Invoker(remap = false)
    public abstract void callBind(ResourceLocation res);
}
