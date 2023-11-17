package com.gtnewhorizons.angelica.compat.nd;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class BufferWriter {
    // Temporarily borrowed from Neodymium

    private final ByteBuffer buf;

    private final FloatBuffer floatBuffer;
    private final ShortBuffer shortBuffer;
    private final IntBuffer intBuffer;

    public BufferWriter(ByteBuffer buf) {
        this.buf = buf;
        this.floatBuffer = buf.asFloatBuffer();
        this.shortBuffer = buf.asShortBuffer();
        this.intBuffer = buf.asIntBuffer();
    }

    private void incrementPosition(int add) {
        buf.position(buf.position() + add);
        floatBuffer.position(buf.position() / 4);
        shortBuffer.position(buf.position() / 2);
        intBuffer.position(buf.position() / 4);
    }

    public void writeFloat(float x) {
        try {
        floatBuffer.put(x);

        incrementPosition(4);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void writeInt(int x) {
        intBuffer.put(x);

        incrementPosition(4);
    }

    public void writeByte(byte x) {
        buf.put(x); // this increments the buffer position by 1

        incrementPosition(0);
    }

    public int position() {
        return buf.position();
    }

    public void writeShort(short s) {
        shortBuffer.put(s);

        incrementPosition(2);
    }

}
