package com.gtnewhorizons.angelica.rendering.celeritas.iris;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

import net.coderbot.iris.vertices.IrisQuadView;

public class CeleritasQuadView implements IrisQuadView {
    private long writePointer;
    private int stride;

    public void setup(long ptr, int stride) {
        this.writePointer = ptr;
        this.stride = stride;
    }

    // VANILLA_LIKE format offsets:
    // Position: floats at 0, 4, 8
    // Color: int at 12
    // TexCoord: floats at 16, 20
    // LightCoord: int at 24

    @Override
    public float x(int index) {
        return memGetFloat(writePointer - stride * (3L - index));
    }

    @Override
    public float y(int index) {
        return memGetFloat(writePointer + 4 - stride * (3L - index));
    }

    @Override
    public float z(int index) {
        return memGetFloat(writePointer + 8 - stride * (3L - index));
    }

    @Override
    public float u(int index) {
        return memGetFloat(writePointer + 16 - stride * (3L - index));
    }

    @Override
    public float v(int index) {
        return memGetFloat(writePointer + 20 - stride * (3L - index));
    }
}
