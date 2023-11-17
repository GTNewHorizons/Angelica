package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

@Mixin(Tessellator.class)
public abstract class MixinTessellator implements ITessellatorInstance {
    @Shadow private static int nativeBufferSize;
    @Shadow public int vertexCount;
    @Shadow public boolean isDrawing;
    @Shadow static ByteBuffer byteBuffer;
    @Shadow private static IntBuffer intBuffer;
    @Shadow private static FloatBuffer floatBuffer;
    @Shadow private static ShortBuffer shortBuffer;

    private ByteBuffer angelica$byteBuffer;
    private IntBuffer angelica$intBuffer;
    private FloatBuffer angelica$floatBuffer;
    private ShortBuffer angelica$shortBuffer;
    public float angelica$normalX;
    public float angelica$normalY;
    public float angelica$normalZ;
    public float angelica$midTextureU;
    public float angelica$midTextureV;

    @Shadow public abstract void reset();
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void killStaticBuffer(CallbackInfo ci) {
        byteBuffer = null;
        intBuffer = null;
        floatBuffer = null;
        shortBuffer = null;
    }

    @Inject(method = "<init>(I)V", at = @At("TAIL"))
    private void angelica$extendBufferConstructor(int bufferSize, CallbackInfo ci) {
        angelica$byteBuffer = GLAllocation.createDirectByteBuffer(bufferSize * 4);
        angelica$intBuffer = angelica$byteBuffer.asIntBuffer();
        angelica$floatBuffer = angelica$byteBuffer.asFloatBuffer();
        angelica$shortBuffer = angelica$byteBuffer.asShortBuffer();
        this.isDrawing = false;
    }

    @Inject(method = "<init>()V", at = @At("TAIL"))
    private void angelica$extendEmptyConstructor(CallbackInfo ci) {
        this.angelica$extendBufferConstructor(nativeBufferSize, null);
    }

    @Inject(method = "setNormal(FFF)V", at = @At("HEAD"))
    private void angelica$captureNormalComponents(float x, float y, float z, CallbackInfo ci) {
        this.angelica$normalX = x;
        this.angelica$normalY = y;
        this.angelica$normalZ = z;
    }

    // Redirect static buffer access to the instance buffers
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;byteBuffer:Ljava/nio/ByteBuffer;"))
    private ByteBuffer modifyByteBufferAccess() {
        return this.angelica$byteBuffer;
    }
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;intBuffer:Ljava/nio/IntBuffer;"))
    private IntBuffer modifyIntBufferAccess() {
        return this.angelica$intBuffer;
    }
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;floatBuffer:Ljava/nio/FloatBuffer;"))
    private FloatBuffer modifyFloatBufferAccess() {
        return this.angelica$floatBuffer;
    }
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;shortBuffer:Ljava/nio/ShortBuffer;"))
    private ShortBuffer modifyShortBufferAccess() {
        return this.angelica$shortBuffer;
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
