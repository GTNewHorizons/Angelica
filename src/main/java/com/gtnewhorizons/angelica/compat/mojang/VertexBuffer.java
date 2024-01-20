package com.gtnewhorizons.angelica.compat.mojang;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;


public class VertexBuffer implements AutoCloseable {
    private int id;
    private int vertexCount;
    private VertexFormat format;
    private int drawMode;

    public VertexBuffer() {
        this.id = GL15.glGenBuffers();
    }

    public VertexBuffer(VertexFormat format, int drawMode) {
        this();
        this.format = format;
        this.drawMode = drawMode;
    }

    public void bind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.id);
    }

    public void unbind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void upload(ByteBuffer buffer, int vertexCount) {
        if (this.id == -1) return;
        this.vertexCount = vertexCount;
        this.bind();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        this.unbind();
    }

    public VertexBuffer upload(ByteBuffer buffer) {
        if(format == null) throw new IllegalStateException("No format specified for VBO upload");
        upload(buffer, buffer.capacity() / format.getVertexSize());
        return this;
    }

    public void close() {
        if (this.id >= 0) {
            GL15.glDeleteBuffers(this.id);
            this.id = -1;
        }
    }

    public void draw(FloatBuffer floatBuffer) {
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMultMatrix(floatBuffer);
        draw();
        GL11.glPopMatrix();
    }

    public void draw() {
        GL11.glDrawArrays(drawMode, 0, this.vertexCount);
    }

    public void setupState() {
        if(format == null) throw new IllegalStateException("No format specified for VBO setup");
        bind();
        format.setupBufferState(0L);
    }

    public void cleanupState() {
        format.clearBufferState();
        unbind();
    }
    public void render() {
        if(format == null) throw new IllegalStateException("No format specified for VBO render");
        bind();
        format.setupBufferState(0L);
        draw();
        format.clearBufferState();
        unbind();
    }
}
