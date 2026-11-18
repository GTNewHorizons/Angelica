package com.gtnewhorizons.angelica.sdlgpu.resource;

import java.nio.ByteBuffer;

public final class MappedRange {
    public int glId;
    public ByteBuffer staging;
    public long offset;
    public long length;
    public boolean invalidate;
    public int accessFlags;
}
