package com.gtnewhorizons.angelica.mixins.late.client.hodgepodge;

import com.gtnewhorizons.angelica.compat.debug.HodgepodgeDebugEntry;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.mitchej123.hodgepodge.client.ChunkGenDebugHandler", remap = false)
public class MixinChunkGenDebugHandler {

    @Inject(
        method = "onRenderGameOverlayText(Lnet/minecraftforge/client/event/RenderGameOverlayEvent$Text;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0,
        expect = 0)
    private void angelica$skipLegacyDebugText(RenderGameOverlayEvent.Text event, CallbackInfo ci) {
        if (HodgepodgeDebugEntry.active()) ci.cancel();
    }
}
