package com.gtnewhorizons.angelica.client;

import java.io.InputStream;

public interface IShaderPack {

    void close();

    InputStream getResourceAsStream(String resName);

}
