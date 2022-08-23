package com.gtnewhorizons.angelica.client;

import java.io.InputStream;

public class ShaderPackNone implements IShaderPack {

    public ShaderPackNone() {}

    @Override
    public void close() {}

    @Override
    public InputStream getResourceAsStream(String resName) {
        return null;
    }
}
