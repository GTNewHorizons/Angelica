package com.gtnewhorizons.angelica.glsm.texture;

import java.nio.ByteBuffer;

public interface TextureStaging {
    ByteBuffer buffer();
    boolean bgra();
    int level();
}
