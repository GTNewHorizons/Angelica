package com.gtnewhorizons.angelica.mixins.late.client.botania;

import com.gtnewhorizons.angelica.compat.debug.BotaniaDebugEntry;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "vazkii.botania.client.core.handler.DebugHandler", remap = false)
public class MixinDebugHandler {

    @Inject(
        method = "onDrawDebugText(Lnet/minecraftforge/client/event/RenderGameOverlayEvent$Text;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0,
        expect = 0)
    private void angelica$skipLegacyDebugText(RenderGameOverlayEvent.Text event, CallbackInfo ci) {
        if (BotaniaDebugEntry.active()) ci.cancel();
    }
}
