package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {
    @Unique private SodiumWorldRenderer renderer;

    @Inject(method="<init>", at=@At("RETURN"))
    private void sodium$initRenderer(Minecraft mc, CallbackInfo ci) {
        this.renderer = SodiumWorldRenderer.create(mc);
    }

    @Inject(method="Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V", at=@At("HEAD"))
    private void sodium$setWorldAndLoadRenderers(WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();
        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }

    }
}
