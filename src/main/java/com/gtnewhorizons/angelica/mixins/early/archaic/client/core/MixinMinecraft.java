package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow private IntegratedServer theIntegratedServer;

    @Shadow public abstract void loadWorld(WorldClient p_71403_1_);

    @Shadow public abstract void displayGuiScreen(GuiScreen p_147108_1_);

    @Shadow public GuiScreen currentScreen;

    /** @reason Makes grass display as fancy regardless of the graphics setting. Matches the appearance of 1.8+ */
    @Redirect(method = "runGameLoop", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z"))
    private boolean getFancyGrass(GameSettings gameSettings) {
        return true;
    }

    /** @reason Removes a call to {@link System#gc()} to make world loading as fast as possible */
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), cancellable = true)
    private void onSystemGC(WorldClient worldClient, String reason, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "checkGLError", at = @At("HEAD"), cancellable = true)
    private void skipErrorCheck(String msg, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(method = "launchIntegratedServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", ordinal = 1))
    private void displayWorkingScreen(Minecraft mc, GuiScreen in) {
        mc.displayGuiScreen(new GuiScreenWorking());
    }

    @Inject(method = "launchIntegratedServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;func_147137_ag()Lnet/minecraft/network/NetworkSystem;", ordinal = 0), cancellable = true)
    private void checkServerStopped(CallbackInfo ci) {
        try {
            Thread.sleep(200L);
        }
        catch (InterruptedException ignored) {}

        if(this.theIntegratedServer.isServerStopped()) {
            loadWorld(null);
            displayGuiScreen(null);
            ci.cancel();
        }
    }

}
