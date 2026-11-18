package com.gtnewhorizons.angelica.mixins.early.notfine.interpolatedtexturemap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jss.notfine.render.InterpolatedIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.ITickableTextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
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
        // Icons registered while Forge skips the first atlas load are thrown away again
        if (((TextureMap) (Object) this).skipFirst) {
            return original;
        }
        TextureAtlasSprite tas = (TextureAtlasSprite) original;
        String textureName = tas.getIconName();
        try {
            if (wantsInterpolation(completeResourceLocation(new ResourceLocation(textureName), 0))) {
                return new InterpolatedIcon(textureName);
            }
        } catch (Exception ignored) {/*Should quietly fail, no need to fail hard*/}
        return original;
    }

    /**
     * Reads only the mcmeta, taken from the same pack {@link FallbackResourceManager#getResource} would take it from.
     * Fetching the resource itself opens the image too, which for generated textures means generating them.
     */
    @Unique
    private static boolean wantsInterpolation(ResourceLocation texture) throws IOException {
        if (!(Minecraft.getMinecraft().getResourceManager() instanceof SimpleReloadableResourceManager manager)) {
            return false;
        }
        if (!(manager.domainResourceManagers.get(texture.getResourceDomain()) instanceof FallbackResourceManager domain)) {
            return false;
        }
        ResourceLocation mcmeta = new ResourceLocation(texture.getResourceDomain(), texture.getResourcePath() + ".mcmeta");
        List<IResourcePack> packs = domain.resourcePacks;
        IResourcePack mcmetaPack = null;
        for (int i = packs.size() - 1; i >= 0; i--) {
            IResourcePack pack = packs.get(i);
            if (mcmetaPack == null && pack.resourceExists(mcmeta)) {
                mcmetaPack = pack;
            }
            if (!pack.resourceExists(texture)) {
                continue;
            }
            if (mcmetaPack == null) {
                return false;
            }
            JsonObject mcmetaJson;
            try (InputStream in = mcmetaPack.getInputStream(mcmeta)) {
                mcmetaJson = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            // Same validation the animation section gets when Minecraft reads it
            if (domain.frmMetadataSerializer.parseMetadataSection("animation", mcmetaJson) == null) {
                return false;
            }
            return mcmetaJson.getAsJsonObject("animation").getAsJsonPrimitive("interpolate").getAsBoolean();
        }
        return false;
    }
}
