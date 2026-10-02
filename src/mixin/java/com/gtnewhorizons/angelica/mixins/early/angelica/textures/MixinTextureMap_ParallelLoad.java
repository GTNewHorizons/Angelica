package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.textures.atlas.AtlasLoadSession;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;
import java.io.InputStream;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap_ParallelLoad {

    @Unique
    private AtlasLoadSession angelica$atlasSession;

    @Inject(
        method = "loadTextureAtlas",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", remap = false))
    private void angelica$beginAtlasSession(IResourceManager manager, CallbackInfo ci) {
        if (this.angelica$atlasSession != null) {
            this.angelica$atlasSession.close();
            this.angelica$atlasSession = null;
        }
        this.angelica$atlasSession = AtlasLoadSession.begin((TextureMap) (Object) this, manager);
    }

    @Surround(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;",
            ordinal = 0),
        id = "resource")
    private void angelica$takePrefetchedResource(IResourceManager manager, ResourceLocation location, @Surround.Local(ordinal = 0) TextureAtlasSprite sprite) {
        final AtlasLoadSession session = this.angelica$atlasSession;
        @Surround.Carry
        final IResource resource = session == null ? null : session.takeResource(manager, sprite, location);
        @Surround.Skip
        final boolean prefetched = resource != null;
    }

    @Surround.Skipped("resource")
    private IResource angelica$prefetchedResource(@Surround.Carry IResource resource) {
        return resource;
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;",
            ordinal = 0,
            remap = false))
    private BufferedImage angelica$takePrefetchedImage(InputStream stream, Operation<BufferedImage> original) {
        final AtlasLoadSession session = this.angelica$atlasSession;
        if (session == null) {
            return original.call(stream);
        }
        final BufferedImage prefetched = session.takeImage(stream);
        if (prefetched != null) {
            return prefetched;
        }
        final BufferedImage image = original.call(stream);
        if (session.wantsDecoder()) {
            session.onMainDecode(s -> original.call(s));
        }
        return image;
    }

    @Inject(
        method = "loadTextureAtlas",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0, remap = false))
    private void angelica$beginMipmaps(IResourceManager manager, CallbackInfo ci) {
        final AtlasLoadSession session = this.angelica$atlasSession;
        if (session != null) {
            final TextureMap map = (TextureMap) (Object) this;
            session.beginMipmaps(map.mapRegisteredSprites.values(), map.mipmapLevels);
        }
    }

    @Surround(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;generateMipmaps(I)V",
            ordinal = 0),
        id = "mipmaps")
    private void angelica$joinMipmaps(TextureAtlasSprite sprite, int levels) {
        final AtlasLoadSession session = this.angelica$atlasSession;
        @Surround.Skip
        final boolean joined = session != null && session.joinMipmaps(sprite, levels);
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void angelica$closeAtlasSession(CallbackInfo ci) {
        final AtlasLoadSession session = this.angelica$atlasSession;
        if (session != null) {
            this.angelica$atlasSession = null;
            session.close();
        }
    }
}
