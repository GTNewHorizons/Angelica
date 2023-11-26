package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.INetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes interdimensional teleportation nearly as fast as same-dimension
 * teleportation by removing the "Downloading terrain..." screen. This will cause
 * the player to see partially loaded terrain rather than waiting for the whole
 * render distance to load, but that's also the vanilla behaviour for same-dimension
 * teleportation. [From ArchaicFix]
 */
@Mixin(value = NetHandlerPlayClient.class, priority = 500)
public abstract class MixinNetHandlerPlayClient implements INetHandlerPlayClient {

    @Redirect(method = "handleJoinGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void onGuiDisplayJoin(Minecraft mc, GuiScreen guiScreenIn) {
        mc.displayGuiScreen(AngelicaConfig.hideDownloadingTerrainScreen ? null : guiScreenIn);
    }

    @Redirect(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void onGuiDisplayRespawn(Minecraft mc, GuiScreen guiScreenIn) {
        mc.displayGuiScreen(AngelicaConfig.hideDownloadingTerrainScreen ? null : guiScreenIn);
    }
}
