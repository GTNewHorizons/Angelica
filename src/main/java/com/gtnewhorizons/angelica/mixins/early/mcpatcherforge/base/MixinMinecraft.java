package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.base;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.Multimap;
import com.prupe.mcpatcher.Config;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.cc.Colorizer;
import com.prupe.mcpatcher.cit.CITUtils;
import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.hd.FontUtils;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;
import com.prupe.mcpatcher.mal.tile.TileLoader;
import com.prupe.mcpatcher.mob.MobRandomizer;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public abstract IResourceManager getResourceManager();

    @Shadow
    @Final
    private static ResourceLocation locationMojangPng;

    @Inject(
        method = "<init>(Lnet/minecraft/util/Session;IIZZLjava/io/File;Ljava/io/File;Ljava/io/File;Ljava/net/Proxy;Ljava/lang/String;Lcom/google/common/collect/Multimap;Ljava/lang/String;)V",
        at = @At("RETURN"))
    private void modifyConstructor(Session sessionIn, int displayWidth, int displayHeight, boolean fullscreen,
        boolean isDemo, File dataDir, File assetsDir, File resourcePackDir, Proxy proxy, String version,
        Multimap<String, String> twitchDetails, String assetsJsonVersion, CallbackInfo ci) {
        MCPatcherUtils.setMinecraft(dataDir);
    }

    @Inject(
        method = "startGame()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IReloadableResourceManager;registerReloadListener(Lnet/minecraft/client/resources/IResourceManagerReloadListener;)V",
            ordinal = 0))
    private void modifyStartGame1(CallbackInfo ci) {
        TileLoader.init();
        CTMUtils.reset();
        if (Config.getBoolean(MCPatcherUtils.CUSTOM_ITEM_TEXTURES, "enabled", true)) {
            CITUtils.init();
        }
        if (Config.getBoolean(MCPatcherUtils.EXTENDED_HD, "enabled", true)) {
            FontUtils.init();
        }
        if (Config.getBoolean(MCPatcherUtils.RANDOM_MOBS, "enabled", true)) {
            MobRandomizer.init();
        }
        if (Config.getBoolean(MCPatcherUtils.CUSTOM_COLORS, "enabled", true)) {
            Colorizer.init();
        }
    }

    @Inject(
        method = "startGame()V",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/FMLClientHandler;beginMinecraftLoading(Lnet/minecraft/client/Minecraft;Ljava/util/List;Lnet/minecraft/client/resources/IReloadableResourceManager;)V",
            remap = false,
            shift = At.Shift.AFTER))
    private void modifyStartGame2(CallbackInfo ci) {
        TexturePackChangeHandler.beforeChange1();
    }

    @Inject(
        method = "startGame()V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V",
            remap = false,
            shift = At.Shift.AFTER))
    private void modifyStartGame3(CallbackInfo ci) {
        TexturePackChangeHandler.afterChange1();
    }

    @Redirect(
        method = "loadScreen()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;getDynamicTextureLocation(Ljava/lang/String;Lnet/minecraft/client/renderer/texture/DynamicTexture;)Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation modifyLoadScreen(TextureManager renderEngine, String p_110578_1_,
        DynamicTexture p_110578_2_) throws IOException {
        return renderEngine.getDynamicTextureLocation(
            "logo",
            new DynamicTexture(
                ImageIO.read(
                    this.getResourceManager()
                        .getResource(locationMojangPng)
                        .getInputStream())));
    }

    @Inject(method = "runGameLoop()V", at = @At(value = "HEAD"))
    private void modifyRunGameLoop(CallbackInfo ci) {
        TexturePackChangeHandler.checkForTexturePackChange();
    }
}
