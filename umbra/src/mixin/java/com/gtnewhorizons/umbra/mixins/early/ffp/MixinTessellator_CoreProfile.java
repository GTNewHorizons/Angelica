package com.gtnewhorizons.umbra.mixins.early.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.ITessellatorData;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Tessellator.draw() to use streaming VBO+VAO for core profile rendering.
 * Implements ITessellatorData so TessellatorStreamingDrawer can access Tessellator fields
 * without depending on Minecraft classes.
 */
@Mixin(Tessellator.class)
public class MixinTessellator_CoreProfile implements ITessellatorData {

    @Shadow public boolean isDrawing;
    @Shadow public int vertexCount;
    @Shadow public int[] rawBuffer;
    @Shadow public int rawBufferIndex;
    @Shadow public int rawBufferSize;
    @Shadow public int drawMode;
    @Shadow public boolean hasTexture;
    @Shadow public boolean hasColor;
    @Shadow public boolean hasNormals;
    @Shadow public boolean hasBrightness;

    @Shadow public void reset() {}

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void umbra$coreProfileDraw(CallbackInfoReturnable<Integer> cir) {
        if (TessellatorManager.shouldInterceptDraw((Tessellator)(Object)this)) return;
        cir.setReturnValue(TessellatorStreamingDrawer.draw((ITessellatorData) this));
    }

    @Override public boolean isDrawing() { return isDrawing; }
    @Override public void setDrawing(boolean drawing) { isDrawing = drawing; }
    @Override public int getVertexCount() { return vertexCount; }
    @Override public int[] getRawBuffer() { return rawBuffer; }
    @Override public int getRawBufferIndex() { return rawBufferIndex; }
    @Override public int getRawBufferSize() { return rawBufferSize; }
    @Override public void setRawBufferSize(int size) { rawBufferSize = size; }
    @Override public void setRawBuffer(int[] buffer) { rawBuffer = buffer; }
    @Override public int getDrawMode() { return drawMode; }
    @Override public boolean hasTexture() { return hasTexture; }
    @Override public boolean hasColor() { return hasColor; }
    @Override public boolean hasNormals() { return hasNormals; }
    @Override public boolean hasBrightness() { return hasBrightness; }
}
