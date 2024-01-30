package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import net.coderbot.iris.texture.pbr.PBRAtlasHolder;
import net.coderbot.iris.texture.pbr.TextureAtlasExtension;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap extends AbstractTexture implements TextureAtlasExtension {
	@Unique
	private PBRAtlasHolder iris$pbrHolder;

	@Inject(method = "updateAnimations()V", at = @At("TAIL"))
	private void iris$onTailUpdateAnimations(CallbackInfo ci) {
		if (iris$pbrHolder != null) {
			iris$pbrHolder.cycleAnimationFrames();
		}
	}

	@Override
	public PBRAtlasHolder getPBRHolder() {
		return iris$pbrHolder;
	}

	@Override
	public PBRAtlasHolder getOrCreatePBRHolder() {
		if (iris$pbrHolder == null) {
			iris$pbrHolder = new PBRAtlasHolder();
		}
		return iris$pbrHolder;
	}
}
