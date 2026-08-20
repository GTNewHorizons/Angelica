package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.profiling.GpuFrameLagMeter;
import com.gtnewhorizons.angelica.profiling.TracyFramePlots;
import com.gtnewhorizons.angelica.profiling.TracyUiSections;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_Tracy {
    private static final Tracy.ZoneId Z_SWAP_BUFFERS = Tracy.zoneId("swapBuffers", Tracy.COLOR_SWAP);

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void angelica$tracyFrameMark(CallbackInfo ci) {
        TracyFramePlots.onFrame((Minecraft) (Object) this);
        GpuFrameLagMeter.onFrame();
        Tracy.frameMark();
        TracyUiSections.poll(((Minecraft) (Object) this).currentScreen);
        Tracy.gpuCollect();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick()V"))
    private void angelica$tracyGpuOffForTick(CallbackInfo ci) {
        Tracy.setGpuZonesEnabled(false);
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick()V", shift = At.Shift.AFTER))
    private void angelica$tracyGpuOnAfterTick(CallbackInfo ci) {
        Tracy.setGpuZonesEnabled(true);
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V"))
    private void angelica$tracySwapBegin(CallbackInfo ci) {
        Tracy.beginZone(Z_SWAP_BUFFERS);
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V", shift = At.Shift.AFTER))
    private void angelica$tracySwapEnd(CallbackInfo ci) {
        Tracy.endZone();
    }

    // runGameLoop opens a second "root" section after the render phase; renaming it keeps
    // client statistics from merging two different scopes under one srcloc.
    @ModifyConstant(method = "runGameLoop", constant = @Constant(stringValue = "root", ordinal = 1))
    private String angelica$tracyRenameSecondRoot(String constant) {
        return "root2";
    }
}
