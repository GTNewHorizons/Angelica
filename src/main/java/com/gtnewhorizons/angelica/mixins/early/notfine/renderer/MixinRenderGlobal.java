package com.gtnewhorizons.angelica.mixins.early.notfine.renderer;

import jss.notfine.core.Settings;
import jss.notfine.render.RenderStars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.IRenderHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.WrapWithCondition;

@Mixin(value = RenderGlobal.class)
public abstract class MixinRenderGlobal {
    
    @Shadow private int glSkyList;
    @Shadow private int starGLCallList;
    @Shadow private int glSkyList2;
    
    @WrapWithCondition(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false))
    private boolean conditionalCallList(int i) {
        if(i == starGLCallList) {
            return (boolean)Settings.MODE_STARS.option.getStore();
        } else {
            return (boolean)Settings.MODE_SKY.option.getStore();
        }
    }
    
    @WrapWithCondition(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/IRenderHandler;render(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V", remap = false))
    private boolean conditionalRenderHandlerRender(IRenderHandler irh, float f, WorldClient wc, Minecraft mc) {
        return (boolean)Settings.MODE_SKY.option.getStore();
    }
    
    @Inject(method="renderSky(F)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0))
    private void conditionalTessellatorDrawEndSkybox(CallbackInfo ci) {
        if(!(boolean)Settings.MODE_SKY.option.getStore()) {
            Tessellator.instance.vertexCount = 0;
        }
    }
    
    @Inject(method="renderSky(F)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 1))
    private void conditionalTessellatorDrawSunsetSunrise(CallbackInfo ci) {
        if(!(boolean)Settings.MODE_SKY.option.getStore()) {
            Tessellator.instance.vertexCount = 0;
        }
    }
    
    @Inject(method="renderSky(F)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 2))
    private void conditionalTessellatorDrawSun(CallbackInfo ci) {
        if(!(boolean)Settings.MODE_SUN_MOON.option.getStore()) {
            Tessellator.instance.vertexCount = 0;
        }
    }
    
    @Inject(method="renderSky(F)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 3))
    private void conditionalTessellatorDrawMoon(CallbackInfo ci) {
        if(!(boolean)Settings.MODE_SUN_MOON.option.getStore()) {
            Tessellator.instance.vertexCount = 0;
        }
    }
    
    @Inject(method="renderSky(F)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 4))
    private void conditionalTessellatorDrawHorizon(CallbackInfo ci) {
        if(!(boolean)Settings.MODE_SKY.option.getStore()) {
            Tessellator.instance.vertexCount = 0;
        }
    }

    /**
     * @author jss2a98aj
     * @reason Control star generation.
     */
    @Overwrite
    private void renderStars() {
        RenderStars.renderStars();
    }

}
