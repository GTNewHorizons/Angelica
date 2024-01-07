package com.gtnewhorizons.angelica.client.renderer;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.util.MathHelper.clamp_int;

/*
 * To be used in conjunction with the TessellatorManager
 *
 * Used to capture the quads generated by the Tessellator across multiple draw calls and make the quad
 * list available for usage.
 *
 * NOTE: This will _not_ (currently) capture, integrate, or stop any GL calls made around the tessellator draw calls.
 *
 */
@SuppressWarnings("unused")
public class CapturingTessellator extends Tessellator implements ITessellatorInstance {

    // Access Transformers don't work on Forge Fields :rage:
    private static final MethodHandle sRawBufferSize;
    private static final MethodHandle gRawBufferSize;
    private final BlockRenderer.Flags FLAGS = new BlockRenderer.Flags(true, true, true, true);
    private final ObjectPooler<Quad> quadBuf = new ObjectPooler<>(Quad::new);
    private final List<Quad> collectedQuads = new ObjectArrayList<>();

    // Any offset we need to the Tesselator's offset!
    private final BlockPos offset = new BlockPos();

    public void setOffset(BlockPos pos) {
        this.offset.set(pos);
    }
    public void resetOffset() {
        this.offset.zero();
    }


    static {
        Field rbs = ReflectionHelper.findField(Tessellator.class, "rawBufferSize");
        rbs.setAccessible(true);
        try {
            sRawBufferSize =  MethodHandles.lookup().unreflectSetter(rbs);
            gRawBufferSize =  MethodHandles.lookup().unreflectGetter(rbs);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setRawBufferSize(int size) {
        try {
            sRawBufferSize.invokeExact((Tessellator)this, size);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    public int getRawBufferSize() {
        try {
            return (int) gRawBufferSize.invokeExact((Tessellator)this);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public int draw() {
        // Adapted from Neodymium

        FLAGS.hasBrightness = this.hasBrightness;
        FLAGS.hasColor = this.hasColor;
        FLAGS.hasNormals = this.hasNormals;

        // TODO: Support GL_TRIANGLES
        if(this.drawMode != GL11.GL_QUADS) {
            throw new IllegalStateException("Currently only supports GL_QUADS");

        }
        final int verticesPerPrimitive = this.drawMode == GL11.GL_QUADS ? 4 : 3;


        for(int quadI = 0; quadI < this.vertexCount / verticesPerPrimitive; quadI++) {
            final Quad quad = quadBuf.getInstance();
            quad.setState(this.rawBuffer, quadI * (verticesPerPrimitive * 8), FLAGS, this.drawMode, -offset.x, -offset.y, -offset.z);

            if(quad.deleted) {
                quadBuf.releaseInstance(quad);
            } else {
                this.collectedQuads.add(quad);
            }
        }

        final int i = this.rawBufferIndex * 4;
        this.discard();
        return i;
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public void discard() {
        isDrawing = false;
        reset();
    }


    public List<Quad> getQuads() {
        return collectedQuads;
    }

    public void clearQuads() {
        //noinspection ForLoopReplaceableByForEach
        for(int i = 0; i < this.collectedQuads.size(); i++) {
            this.quadBuf.releaseInstance(this.collectedQuads.get(i));
        }
        this.collectedQuads.clear();
    }

    public static ByteBuffer quadsToBuffer(List<Quad> quads, VertexFormat format) {
        if(!format.canWriteQuads()) {
            throw new IllegalStateException("Vertex format has no quad writer: " + format);
        }
        final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(format.getVertexSize() * quads.size() * 4);
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            format.writeQuad(quads.get(i), byteBuffer);
        }
        byteBuffer.rewind();
        return byteBuffer;
    }

    public static int createBrightness(int sky, int block) {
        return sky << 20 | block << 4;
    }

    // API from newer MC
    public CapturingTessellator pos(double x, double y, double z) {
        ensureBuffer();

        this.rawBuffer[this.rawBufferIndex + 0] = Float.floatToRawIntBits((float)(x + this.xOffset));
        this.rawBuffer[this.rawBufferIndex + 1] = Float.floatToRawIntBits((float)(y + this.yOffset));
        this.rawBuffer[this.rawBufferIndex + 2] = Float.floatToRawIntBits((float)(z + this.zOffset));

        return this;
    }
    public CapturingTessellator tex(double u, double v) {
        this.rawBuffer[this.rawBufferIndex + 3] = Float.floatToRawIntBits((float)u);
        this.rawBuffer[this.rawBufferIndex + 4] = Float.floatToRawIntBits((float)v);
        this.hasTexture = true;

        return this;
    }

    public CapturingTessellator color(float red, float green, float blue, float alpha) {
        return this.color((int)(red * 255.0F), (int)(green * 255.0F), (int)(blue * 255.0F), (int)(alpha * 255.0F));
    }

    public CapturingTessellator color(int red, int green, int blue, int alpha) {
        if(this.isColorDisabled) return this;
        red = clamp_int(red, 0, 255);
        green = clamp_int(green, 0, 255);
        blue = clamp_int(blue, 0, 255);
        alpha = clamp_int(alpha, 0, 255);

        final int color;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            color = alpha << 24 | blue << 16 | green << 8 | red;
        } else {
            color = red << 24 | green << 16 | blue << 8 | alpha;
        }
        this.rawBuffer[this.rawBufferIndex + 5] = color;
        this.hasColor = true;

        return this;

    }

    public CapturingTessellator normal(float x, float y, float z) {
        final byte b0 = (byte)((int)(x * 127.0F));
        final byte b1 = (byte)((int)(y * 127.0F));
        final byte b2 = (byte)((int)(z * 127.0F));

        this.rawBuffer[this.rawBufferIndex + 6] = b0 & 255 | (b1 & 255) << 8 | (b2 & 255) << 16;
        this.hasNormals = true;
        return this;
    }

    public CapturingTessellator lightmap(int skyLight, int blockLight) {
        return brightness(createBrightness(skyLight, blockLight));
    }

    public CapturingTessellator brightness(int brightness) {
        this.rawBuffer[this.rawBufferIndex + 7] = brightness;
        this.hasBrightness = true;

        return this;
    }

    public CapturingTessellator endVertex() {
        this.rawBufferIndex += 8;
        ++this.vertexCount;

        return this;
    }

    public void ensureBuffer() {
        int rawBufferSize = getRawBufferSize();
        if (rawBufferIndex >= rawBufferSize - 32) {
            if (rawBufferSize == 0) {
                rawBufferSize = 0x10000;
                setRawBufferSize(rawBufferSize);
                rawBuffer = new int[rawBufferSize];
            } else {
                rawBufferSize *= 2;
                setRawBufferSize(rawBufferSize);
                rawBuffer = Arrays.copyOf(rawBuffer, rawBufferSize);
            }
        }
    }

}
