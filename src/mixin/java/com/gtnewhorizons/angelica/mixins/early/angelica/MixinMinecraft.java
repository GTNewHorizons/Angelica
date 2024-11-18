package com.gtnewhorizons.angelica.mixins.early.angelica;

import static com.gtnewhorizons.angelica.debug.FrametimeGraph.NUM_FRAMETIMES;
import static com.gtnewhorizons.angelica.debug.FrametimeGraph.frametimes;
import static com.gtnewhorizons.angelica.debug.FrametimeGraph.frametimesHead;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Unique
    private long angelica$lastFrameTime = 0;

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void angelica$injectLightingFixPostRenderTick(CallbackInfo ci) {
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    @Inject(
        method = "func_147120_f",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", shift = At.Shift.AFTER)
    )
    private void angelica$trackFrametimes(CallbackInfo ci) {
        long time = System.nanoTime();
        frametimes[frametimesHead] = time - angelica$lastFrameTime;
        frametimesHead = (frametimesHead + 1) % NUM_FRAMETIMES;
        angelica$lastFrameTime = time;
    }
}
