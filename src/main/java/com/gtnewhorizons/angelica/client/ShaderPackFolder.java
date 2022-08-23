package com.gtnewhorizons.angelica.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class ShaderPackFolder implements IShaderPack {

    protected File packFile;

    public ShaderPackFolder(String name, File file) {
        packFile = file;
    }

    @Override
    public void close() {}

    @Override
    public InputStream getResourceAsStream(String resName) {
        try {
            File resFile = new File(packFile, resName.substring(1));
            if (resFile != null) {
                return new BufferedInputStream(new FileInputStream(resFile));
            }
        } catch (Exception excp) {
        }
        return null;
    }
}
