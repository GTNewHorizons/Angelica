package com.gtnewhorizons.angelica.client.streaming;

public interface VAOConsumer {

    /**
     * Passes in a vertex array object (VAO) and a vertex buffer object (VBO) and expects the program to initialize
     * the VAO with the correct pointers using things such as {@link org.lwjgl.opengl.GL20#glVertexAttribPointer}.
     * @return The Data size that the VAO expects
     */
    int initialize(int vao, int vbo);
}
