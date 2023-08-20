package com.gtnewhorizons.angelica.mixins.interfaces;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public interface TessellatorAccessor {

    ByteBuffer angelica$getByteBuffer();

    IntBuffer angelica$getIntBuffer();

    FloatBuffer angelica$getFloatBuffer();

    ShortBuffer angelica$getShortBuffer();

    float[] angelica$getVertexPos();

    float angelica$getNormalX();

    float angelica$getNormalY();

    float angelica$getNormalZ();

    float angelica$getMidTextureU();

    float angelica$getMidTextureV();

    void angelica$setByteBuffer(ByteBuffer angelica$byteBuffer);

    void angelica$setIntBuffer(IntBuffer angelica$intBuffer);

    void angelica$setFloatBuffer(FloatBuffer angelica$floatBuffer);

    void angelica$setShortBuffer(ShortBuffer angelica$shortBuffer);

    void angelica$setVertexPos(float[] angelica$vertexPos);

    void angelica$setNormalX(float angelica$normalX);

    void angelica$setNormalY(float angelica$normalY);

    void angelica$setNormalZ(float angelica$normalZ);

    void angelica$setMidTextureU(float angelica$midTextureU);

    void angelica$setMidTextureV(float angelica$midTextureV);
}
