package org.embeddedt.archaicfix.mixins.client.lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow @Final public Profiler mcProfiler;

    @Shadow public WorldClient theWorld;

    /**
     * @author Angeline
     * Forces the client to process light updates before rendering the world. We inject before the call to the profiler
     * which designates the start of world rendering. This is a rather injection site.
     */
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=levelRenderer", shift = At.Shift.BY, by = -3))
    private void onRunTick(CallbackInfo ci) {
        this.mcProfiler.endStartSection("lighting");

        ((ILightingEngineProvider) this.theWorld).getLightingEngine().processLightUpdates();
    }

}
