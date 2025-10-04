package com.gtnewhorizons.angelica.mixins.early.chunkbert;

import com.embeddedt.chunkbert.FakeChunkManager;
import com.embeddedt.chunkbert.ext.IChunkProviderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Final
    @Shadow
    public Profiler mcProfiler;

    @Shadow public WorldClient theWorld;

    @Shadow public GameSettings gameSettings;

    @Inject(method = "runGameLoop", at = @At(value = "CONSTANT", args = "stringValue=tick"))
    private void bobbyUpdate(CallbackInfo ci) {
        if (theWorld == null) {
            return;
        }
        FakeChunkManager bobbyChunkManager = ((IChunkProviderClient) theWorld.getChunkProvider()).chunkbert$getChunkManager();
        if (bobbyChunkManager == null) {
            return;
        }

        mcProfiler.startSection("bobbyUpdate");

        int maxFps = gameSettings.limitFramerate;
        long frameTime = 1_000_000_000 / (maxFps == GameSettings.Options.FRAMERATE_LIMIT.getValueMax() ? 120 : maxFps);
        // Arbitrarily choosing 1/4 of frame time as our max budget, that way we're hopefully not noticeable.
        long frameBudget = frameTime / 4;
        long timeLimit = (System.nanoTime() / 1000000L) + frameBudget;
        bobbyChunkManager.update(() -> (System.nanoTime() / 1000000L) < timeLimit);

        mcProfiler.endSection();
    }
}
