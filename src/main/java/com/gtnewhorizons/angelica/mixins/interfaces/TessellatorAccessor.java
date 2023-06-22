package com.gtnewhorizons.angelica.mixins.interfaces;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public interface TessellatorAccessor {

    ByteBuffer getAngelica$byteBuffer();

    IntBuffer getAngelica$intBuffer();

    FloatBuffer getAngelica$floatBuffer();

    ShortBuffer getAngelica$shortBuffer();

    float[] getAngelica$vertexPos();

    float getAngelica$normalX();

    float getAngelica$normalY();

    float getAngelica$normalZ();

    float getAngelica$midTextureU();

    float getAngelica$midTextureV();

    void setAngelica$byteBuffer(ByteBuffer angelica$byteBuffer);

    void setAngelica$intBuffer(IntBuffer angelica$intBuffer);

    void setAngelica$floatBuffer(FloatBuffer angelica$floatBuffer);

    void setAngelica$shortBuffer(ShortBuffer angelica$shortBuffer);

    void setAngelica$vertexPos(float[] angelica$vertexPos);

    void setAngelica$normalX(float angelica$normalX);

    void setAngelica$normalY(float angelica$normalY);

    void setAngelica$normalZ(float angelica$normalZ);

    void setAngelica$midTextureU(float angelica$midTextureU);

    void setAngelica$midTextureV(float angelica$midTextureV);
}
