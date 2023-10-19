package net.coderbot.iris.compat.mojang;

import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;

public class BufferBuilder implements VertexConsumer {
    int i;

    public BufferBuilder() {
        this(0);
    }

    public BufferBuilder(int i) {
        this.i = i;
    }

    public void begin(int glQuads, VertexFormat position) {}

    public void end() {}

    @Override
    public VertexConsumer vertex(double d, double e, double f) {
        return null;
    }

    @Override
    public void endVertex() {

    }

    public void sortQuads(int i, int i1, int i2) {}

    public Pair<DrawState, ByteBuffer> popNextBuffer() {
        return null;
    }

    public void clear() {}
}
