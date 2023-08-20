package com.gtnewhorizons.angelica.mixins.early.renderer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.PriorityQueue;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.client.util.QuadComparator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.ShadersTess;
import com.gtnewhorizons.angelica.mixins.interfaces.TessellatorAccessor;

@Mixin(Tessellator.class)
public abstract class MixinTessellator implements TessellatorAccessor {

    @Shadow(remap = false)
    public boolean defaultTexture;
    @Shadow(remap = false)
    private int rawBufferSize;
    @Shadow(remap = false)
    public int textureID;
    @Shadow
    public int[] rawBuffer;
    @Shadow
    public int vertexCount;
    @Shadow
    public int rawBufferIndex;
    @Shadow
    public int addedVertices;
    @Shadow
    public boolean hasTexture;
    @Shadow
    public boolean hasBrightness;
    @Shadow
    public boolean hasNormals;
    @Shadow
    public boolean hasColor;
    @Shadow
    public double xOffset;
    @Shadow
    public double yOffset;
    @Shadow
    public double zOffset;
    private ByteBuffer angelica$byteBuffer;
    private IntBuffer angelica$intBuffer;
    private FloatBuffer angelica$floatBuffer;
    private ShortBuffer angelica$shortBuffer;
    public float[] angelica$vertexPos;
    public float angelica$normalX;
    public float angelica$normalY;
    public float angelica$normalZ;
    public float angelica$midTextureU;
    public float angelica$midTextureV;

    @Inject(method = "<init>(I)V", at = @At("TAIL"))
    private void angelica$extendBufferConstructor(int bufferSize, CallbackInfo ci) {
        angelica$byteBuffer = GLAllocation.createDirectByteBuffer(bufferSize * 4);
        angelica$intBuffer = angelica$byteBuffer.asIntBuffer();
        angelica$floatBuffer = angelica$byteBuffer.asFloatBuffer();
        angelica$shortBuffer = angelica$byteBuffer.asShortBuffer();
        rawBuffer = new int[bufferSize];
        vertexCount = 0;
        angelica$vertexPos = new float[16];
    }

    @Inject(method = "<init>()V", at = @At("TAIL"))
    private void angelica$extendEmptyConstructor(CallbackInfo ci) {
        this.angelica$extendBufferConstructor(65536, null);
        this.defaultTexture = false;
        this.rawBufferSize = 0;
        this.textureID = 0;
    }

    /**
     * @author eigenraven
     * @reason The entire drawing process must be redirected to go through an alternative renderer
     */
    @Overwrite
    public int draw() {
        return ShadersTess.draw((Tessellator) (Object) this);
    }

    /**
     * @author eigenraven
     * @reason byteBuffer->angelica$byteBuffer
     */
    @Overwrite
    public void reset() {
        this.vertexCount = 0;
        this.angelica$byteBuffer.clear();
        this.rawBufferIndex = 0;
        this.addedVertices = 0;
    }

    /**
     * @author eigenraven
     * @reason The entire drawing process must be redirected to go through an alternative renderer
     */
    @Overwrite
    public void addVertex(double x, double y, double z) {
        ShadersTess.addVertex((Tessellator) (Object) this, x, y, z);
    }

    /**
     * @author eigenraven
     * @reason Modify 32->64, ModifyConstant causes an internal mixin crash in this method
     */
    @Overwrite
    public TesselatorVertexState getVertexState(float x, float y, float z) {
        int[] tmpCopyBuffer = new int[this.rawBufferIndex];
        @SuppressWarnings("unchecked")
        PriorityQueue<Integer> pQueue = new PriorityQueue<>(
                this.rawBufferIndex,
                new QuadComparator(
                        this.rawBuffer,
                        x + (float) this.xOffset,
                        y + (float) this.yOffset,
                        z + (float) this.zOffset));
        byte batchSize = 64;

        for (int vidx = 0; vidx < this.rawBufferIndex; vidx += batchSize) {
            pQueue.add(vidx);
        }

        for (int batchVidx = 0; !pQueue.isEmpty(); batchVidx += batchSize) {
            int queuedVidx = pQueue.remove();

            for (int batchElement = 0; batchElement < batchSize; ++batchElement) {
                tmpCopyBuffer[batchVidx + batchElement] = this.rawBuffer[queuedVidx + batchElement];
            }
        }

        System.arraycopy(tmpCopyBuffer, 0, this.rawBuffer, 0, tmpCopyBuffer.length);
        return new TesselatorVertexState(
                tmpCopyBuffer,
                this.rawBufferIndex,
                this.vertexCount,
                this.hasTexture,
                this.hasBrightness,
                this.hasNormals,
                this.hasColor);
    }

    @Inject(method = "setNormal(FFF)V", at = @At("HEAD"))
    private void angelica$captureNormalComponents(float x, float y, float z, CallbackInfo ci) {
        this.angelica$normalX = x;
        this.angelica$normalY = y;
        this.angelica$normalZ = z;
    }

    @Override
    public ByteBuffer angelica$getByteBuffer() {
        return angelica$byteBuffer;
    }

    @Override
    public IntBuffer angelica$getIntBuffer() {
        return angelica$intBuffer;
    }

    @Override
    public FloatBuffer angelica$getFloatBuffer() {
        return angelica$floatBuffer;
    }

    @Override
    public ShortBuffer angelica$getShortBuffer() {
        return angelica$shortBuffer;
    }

    @Override
    public float[] angelica$getVertexPos() {
        return angelica$vertexPos;
    }

    @Override
    public float angelica$getNormalX() {
        return angelica$normalX;
    }

    @Override
    public float angelica$getNormalY() {
        return angelica$normalY;
    }

    @Override
    public float angelica$getNormalZ() {
        return angelica$normalZ;
    }

    @Override
    public float angelica$getMidTextureU() {
        return angelica$midTextureU;
    }

    @Override
    public float angelica$getMidTextureV() {
        return angelica$midTextureV;
    }

    @Override
    public void angelica$setByteBuffer(ByteBuffer angelica$byteBuffer) {
        this.angelica$byteBuffer = angelica$byteBuffer;
    }

    @Override
    public void angelica$setIntBuffer(IntBuffer angelica$intBuffer) {
        this.angelica$intBuffer = angelica$intBuffer;
    }

    @Override
    public void angelica$setFloatBuffer(FloatBuffer angelica$floatBuffer) {
        this.angelica$floatBuffer = angelica$floatBuffer;
    }

    @Override
    public void angelica$setShortBuffer(ShortBuffer angelica$shortBuffer) {
        this.angelica$shortBuffer = angelica$shortBuffer;
    }

    @Override
    public void angelica$setVertexPos(float[] angelica$vertexPos) {
        this.angelica$vertexPos = angelica$vertexPos;
    }

    @Override
    public void angelica$setNormalX(float angelica$normalX) {
        this.angelica$normalX = angelica$normalX;
    }

    @Override
    public void angelica$setNormalY(float angelica$normalY) {
        this.angelica$normalY = angelica$normalY;
    }

    @Override
    public void angelica$setNormalZ(float angelica$normalZ) {
        this.angelica$normalZ = angelica$normalZ;
    }

    @Override
    public void angelica$setMidTextureU(float angelica$midTextureU) {
        this.angelica$midTextureU = angelica$midTextureU;
    }

    @Override
    public void angelica$setMidTextureV(float angelica$midTextureV) {
        this.angelica$midTextureV = angelica$midTextureV;
    }
}
