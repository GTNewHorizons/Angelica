package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Removes a start of tons new threads
 * for skins loading
 */
@Mixin(ThreadDownloadImageData.class)
public final class MixinThreadDownloadImageData {
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), new ThreadFactoryBuilder().setNameFormat("Skin Downloader #%d").setDaemon(true).setPriority(Thread.MIN_PRIORITY).build());

    @Redirect(method = "func_152433_a", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"))
    private void onThreadStart(Thread thread) {
        EXECUTOR.execute(thread);
    }
}
