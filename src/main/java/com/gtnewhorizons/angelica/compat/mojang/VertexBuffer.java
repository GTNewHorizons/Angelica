package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;


public class VertexBuffer implements AutoCloseable {
    private int id;
    private int vertexCount;

    public VertexBuffer() {
        this.id = GL15.glGenBuffers();
    }

    public void bind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.id);
    }

    public void unbind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void upload(Tessellator tessellator, VertexFormat format) {
        try {
            this.upload(tessellatorToBuffer(tessellator, format), tessellator.vertexCount);
        } catch(Exception e) {
            throw new RuntimeException("Failed to upload vertex buffer", e);
        } finally {
            ((ITessellatorInstance) tessellator).discard();
        }
    }

    public ByteBuffer tessellatorToBuffer(Tessellator tessellator, VertexFormat format) {

        final int verticesPerPrimitive = tessellator.drawMode == GL11.GL_QUADS ? 4 : 3;

        final int[] rawBuffer = tessellator.rawBuffer;
        final int byteSize = format.getVertexSize() * tessellator.vertexCount * 4;
        final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(byteSize);



        for(int quadI = 0 ; quadI < tessellator.vertexCount / verticesPerPrimitive ; quadI++) {
            for(int vertexI = 0 ; vertexI < 4 ; vertexI++) {
                final int i = (quadI * 4 * 8 ) + (vertexI * 8);

                // Position
                byteBuffer.putFloat(Float.intBitsToFloat(rawBuffer[i + 0]));
                byteBuffer.putFloat(Float.intBitsToFloat(rawBuffer[i + 1]));
                byteBuffer.putFloat(Float.intBitsToFloat(rawBuffer[i + 2]));

                // Texture
                byteBuffer.putFloat(Float.intBitsToFloat(rawBuffer[i + 3]));
                byteBuffer.putFloat(Float.intBitsToFloat(rawBuffer[i + 4]));

                // Normals
                final int normals = rawBuffer[i + 6];
                byteBuffer.put((byte)(normals & 255));
                byteBuffer.put((byte)((normals >> 8) & 255));
                byteBuffer.put((byte)((normals >> 16) & 255));
            }
        }

        return (ByteBuffer) byteBuffer.rewind();
    }

    public void upload(ByteBuffer buffer, int vertexCount) {
        if (this.id == -1) return;
        this.vertexCount = vertexCount;
        this.bind();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        this.unbind();
    }

    public void close() {
        if (this.id >= 0) {
            GL15.glDeleteBuffers(this.id);
            this.id = -1;
        }
    }

    public void draw(FloatBuffer floatBuffer, int mode) {
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMultMatrix(floatBuffer);
        draw(mode);
        GL11.glPopMatrix();
    }

    public void draw(int mode) {
        GL11.glDrawArrays(mode, 0, this.vertexCount);
    }
}
