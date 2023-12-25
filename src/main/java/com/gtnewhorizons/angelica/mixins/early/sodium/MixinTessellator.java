package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.Buffer;
import java.nio.ByteBuffer;

@Mixin(Tessellator.class)
public abstract class MixinTessellator implements ITessellatorInstance {
    @Shadow public int vertexCount;
    @Shadow public boolean isDrawing;
    public float angelica$normalX;
    public float angelica$normalY;
    public float angelica$normalZ;
    public float angelica$midTextureU;
    public float angelica$midTextureV;

    @Shadow public abstract void reset();

    /**
     * @reason Allow using multiple tessellator instances concurrently by removing static field access in alternate instances.
     **/
    @Redirect(method = "reset", at = @At(value = "INVOKE", target = "Ljava/nio/ByteBuffer;clear()Ljava/nio/Buffer;"))
    private Buffer removeStaticBufferResetOutsideSingleton(ByteBuffer buffer) {
        if(TessellatorManager.isMainInstance(this)) {
            return buffer.clear();
        }
        return buffer;
    }

    @Inject(method="draw", at=@At("HEAD"))
    private void preventOffMainThreadDrawing(CallbackInfoReturnable<Integer> cir) {
        if(!TessellatorManager.isMainInstance(this)) {
            throw new RuntimeException("Tried to draw on a tessellator that isn't on the main thread!");
        }
    }

    @Inject(method = "setNormal(FFF)V", at = @At("HEAD"))
    private void angelica$captureNormalComponents(float x, float y, float z, CallbackInfo ci) {
        this.angelica$normalX = x;
        this.angelica$normalY = y;
        this.angelica$normalZ = z;
    }


    // New methods from ITesselatorInstance

    @Override
    public float getNormalX() {
        return angelica$normalX;
    }

    @Override
    public float getNormalY() {
        return angelica$normalY;
    }

    @Override
    public float getNormalZ() {
        return angelica$normalZ;
    }

    @Override
    public void discard() {
        isDrawing = false;
        reset();
    }

    @Override
    public float getMidTextureU() {
        return angelica$midTextureU;
    }

    @Override
    public float getMidTextureV() {
        return angelica$midTextureV;
    }



}
