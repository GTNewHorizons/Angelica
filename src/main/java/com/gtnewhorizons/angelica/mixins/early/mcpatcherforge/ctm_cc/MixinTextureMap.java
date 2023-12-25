package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc;

import java.util.Map;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.mal.tile.TileLoader;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap extends AbstractTexture {

    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapRegisteredSprites;
    @Shadow
    @Final
    private String basePath;

    @Shadow
    protected abstract void registerIcons();

    @Inject(
        method = "loadTextureAtlas(Lnet/minecraft/client/resources/IResourceManager;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", remap = false, shift = At.Shift.AFTER))
    private void modifyLoadTextureAtlas(IResourceManager manager, CallbackInfo ci) {
        this.registerIcons();
        TileLoader.registerIcons((TextureMap) (Object) this, this.basePath, this.mapRegisteredSprites);
    }

    @Redirect(
        method = "completeResourceLocation(Lnet/minecraft/util/ResourceLocation;I)Lnet/minecraft/util/ResourceLocation;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
            ordinal = 0,
            remap = false))
    private String modifyCompleteResourceLocation(String format, Object[] args, ResourceLocation location,
        int p_147634_2_) {
        return TileLoader.getOverridePath("", this.basePath, location.getResourcePath(), ".png");
    }

    // Base game has s.indexOf(47) != -1 || s.indexOf(92) != -1
    // However, forge already removes this, so we don't have to patch that
    @Redirect(
        method = "registerIcon(Ljava/lang/String;)Lnet/minecraft/util/IIcon;",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
    private boolean modifyRegisterIcon(String instance, Object toCompare) {
        return TileLoader.isSpecialTexture((TextureMap) (Object) this, toCompare.toString(), instance);
    }
}
