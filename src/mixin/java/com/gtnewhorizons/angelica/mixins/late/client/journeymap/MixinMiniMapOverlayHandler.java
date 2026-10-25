package com.gtnewhorizons.angelica.mixins.late.client.journeymap;

import com.gtnewhorizons.angelica.compat.debug.JourneyMapDebugEntry;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "journeymap.client.forge.event.MiniMapOverlayHandler", remap = false)
public class MixinMiniMapOverlayHandler {

    @Inject(
        method = "onRenderOverlayDebug(Lnet/minecraftforge/client/event/RenderGameOverlayEvent$Text;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0,
        expect = 0)
    private void angelica$skipLegacyDebugText(RenderGameOverlayEvent.Text event, CallbackInfo ci) {
        if (JourneyMapDebugEntry.active()) ci.cancel();
    }
}
