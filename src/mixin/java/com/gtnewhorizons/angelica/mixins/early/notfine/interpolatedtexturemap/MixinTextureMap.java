package com.gtnewhorizons.angelica.mixins.early.notfine.interpolatedtexturemap;

import com.google.gson.JsonObject;
import jss.notfine.render.InterpolatedIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.ITickableTextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap extends AbstractTexture implements ITickableTextureObject, IIconRegister {

	@Shadow
	@Final
	private Map<String, TextureAtlasSprite> mapRegisteredSprites;

	@Shadow
	protected abstract ResourceLocation completeResourceLocation(ResourceLocation p_147634_1_, int p_147634_2_);

	@Inject(method = "registerIcon", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;<init>(Ljava/lang/String;)V"), cancellable = true)
	private void registerInterpolatedIcon(String textureName, CallbackInfoReturnable<IIcon> cir) {
		try {
			IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(completeResourceLocation(new ResourceLocation(textureName), 0));
			if (resource instanceof SimpleResource) {
				//This returns IMetadataSections, which seems to already remove unused mcmeta fields in 1.7.10
				//I'm just running this to populate the mcmetaJson field more easily; this does it for us
				if (resource.getMetadata("animation") != null) {
					JsonObject mcmetaJson = ((SimpleResource) resource).mcmetaJson;
					if (mcmetaJson.getAsJsonObject("animation").getAsJsonPrimitive("interpolate").getAsBoolean()) {
						InterpolatedIcon interpolatedIcon = new InterpolatedIcon(textureName);
						mapRegisteredSprites.put(textureName, interpolatedIcon);
						cir.setReturnValue(interpolatedIcon);
					}
				}
			}
		} catch (Exception ignored) {/*Should quietly fail, no need to fail hard*/}
	}
}
