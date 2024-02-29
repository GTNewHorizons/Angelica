package com.gtnewhorizons.angelica.compat.nd;

import com.gtnewhorizons.angelica.api.QuadView;

import java.nio.ByteBuffer;

public interface IWriteQuads {
     void writeQuad(QuadView quad, ByteBuffer buf);
}
