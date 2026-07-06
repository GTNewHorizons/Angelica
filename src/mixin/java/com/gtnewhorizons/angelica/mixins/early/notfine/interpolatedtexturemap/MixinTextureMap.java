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
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap extends AbstractTexture implements ITickableTextureObject, IIconRegister {

	@Shadow
    public abstract ResourceLocation completeResourceLocation(ResourceLocation p_147634_1_, int p_147634_2_);

    @ModifyVariable(method = "registerIcon", at = @At(value = "STORE", ordinal = 3))
    private Object registerInterpolatedIcon(Object original){
        return createInterpolatedIcon(original);
    }

    @ModifyVariable(method = "registerIcon", at = @At(value = "STORE", ordinal = 4))
    private Object registerInterpolatedIcon2(Object original){
        return createInterpolatedIcon(original);
    }

    @Unique
    private Object createInterpolatedIcon(Object original){
        TextureAtlasSprite tas = (TextureAtlasSprite) original;
        String textureName = tas.getIconName();
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(completeResourceLocation(new ResourceLocation(textureName), 0));
            if (resource instanceof SimpleResource) {
                //This returns IMetadataSections, which seems to already remove unused mcmeta fields in 1.7.10
                //I'm just running this to populate the mcmetaJson field more easily; this does it for us
                if (resource.getMetadata("animation") != null) {
                    JsonObject mcmetaJson = ((SimpleResource) resource).mcmetaJson;
                    if (mcmetaJson.getAsJsonObject("animation").getAsJsonPrimitive("interpolate").getAsBoolean()) {
                        return new InterpolatedIcon(textureName);
                    }
                }
            }
        } catch (Exception ignored) {/*Should quietly fail, no need to fail hard*/}
        return original;
    }
}
