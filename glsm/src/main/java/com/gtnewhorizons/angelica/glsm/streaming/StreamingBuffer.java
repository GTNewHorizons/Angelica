package com.gtnewhorizons.angelica.glsm.streaming;

import java.nio.ByteBuffer;

public interface StreamingBuffer {

    int upload(ByteBuffer data, int vertexStride);
    int getBufferId();
    void postDraw();
    int getCapacity();
    void destroy();
}
