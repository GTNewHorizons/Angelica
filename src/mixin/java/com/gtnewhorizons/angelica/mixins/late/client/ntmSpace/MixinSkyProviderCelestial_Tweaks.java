package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.config.CompatConfig;
import com.hbm.dim.SkyProviderCelestial;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import jss.notfine.core.Settings;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyProviderCelestial.class, priority = 100)
public class MixinSkyProviderCelestial_Tweaks {
	
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 2), remap = false)
	private boolean angelica$disableHorizon(int i) {
		return !(boolean) Settings.HORIZON_DISABLE.option.getStore();
	}
	
	@Inject(method = "render", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 1))
	private void angelica$disableAltitudePlanetRenderer(CallbackInfo ci) {
		if(CompatConfig.NTMSpace_disableAltitudePlanetRenderer == CompatConfig.altitudePlanetRendererState.Always || (IrisApi.getInstance().isShaderPackInUse() && CompatConfig.NTMSpace_disableAltitudePlanetRenderer == CompatConfig.altitudePlanetRendererState.ShadersOnly)) {
			Tessellator.instance.vertexCount = 0;
		}
	}
	
}
