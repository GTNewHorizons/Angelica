package com.gtnewhorizons.angelica.compat.mojang;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

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

    private void uploadBuffer(ByteBuffer buffer, int vertexCount) {
        this.vertexCount = vertexCount;
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
    }

    public VertexBuffer upload(ByteBuffer buffer, boolean vaoActive) {
        if(format == null || this.id == -1) throw new IllegalStateException("No format specified for VBO upload");
        this.bind();
        uploadBuffer(buffer, buffer.remaining() / format.getVertexSize());

        // Setup the format if we have an active VAO
        if(vaoActive) setupFormat();

        this.unbind();

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
    public void drawInstanced(int count) {
        GL31.glDrawArraysInstanced(drawMode, 0, this.vertexCount, count);
    }

    public void setupFormat() {
        if(format == null) throw new IllegalStateException("No format specified for VBO setup");
        format.setupBufferState(0L);
    }

    public void cleanupFormat() {
        if(format == null) throw new IllegalStateException("No format specified for VBO cleanup");
        format.clearBufferState();
    }
    public void setupState() {
        bind();
        setupFormat();
    }
    public void cleanupState() {
        cleanupFormat();
        unbind();
    }
    public void render() {
        setupState();
        draw();
        cleanupState();
    }
    public void renderInstanced(int count) {
        // Assuming a VAO
        drawInstanced(count);
    }
}
