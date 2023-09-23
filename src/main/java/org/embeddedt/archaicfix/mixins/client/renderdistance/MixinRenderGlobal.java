package org.embeddedt.archaicfix.mixins.client.renderdistance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderList;
import org.embeddedt.archaicfix.ArchaicFix;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.Buffer;
import java.nio.IntBuffer;

@Mixin(value = RenderGlobal.class, priority = -10)
public class MixinRenderGlobal {
    @Shadow private RenderList[] allRenderLists;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I"), index = 0)
    private int generateDisplayLists(int original) {
        int chunkNum = (ArchaicConfig.newMaxRenderDistance * 2) + 2;
        return chunkNum * chunkNum * 16 * 3;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GLAllocation;createDirectIntBuffer(I)Ljava/nio/IntBuffer;"), index = 0)
    private int createOcclusionBuffer(int original) {
        int chunkNum = (ArchaicConfig.newMaxRenderDistance * 2) + 2;
        return chunkNum * chunkNum * 16;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/nio/IntBuffer;limit(I)Ljava/nio/Buffer;"), index = 0)
    private int limitOcclusionBuffer(int i) {
        int chunkNum = (ArchaicConfig.newMaxRenderDistance * 2) + 2;
        return chunkNum * chunkNum * 16;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void resizeAllRenderLists(Minecraft p_i1249_1_, CallbackInfo ci) {
        int sideLength = Math.max(2, ArchaicConfig.newMaxRenderDistance / 16);
        if(allRenderLists.length >= (sideLength * sideLength))
            return;
        allRenderLists = new RenderList[sideLength*sideLength];
        for(int i = 0; i < allRenderLists.length; i++) {
            allRenderLists[i] = new RenderList();
        }
    }
}
