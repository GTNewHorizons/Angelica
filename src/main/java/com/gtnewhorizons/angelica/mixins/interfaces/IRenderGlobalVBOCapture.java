package com.gtnewhorizons.angelica.mixins.interfaces;

public interface IRenderGlobalVBOCapture {
    void startStarsVBO(int list, int mode);
    void finishStarsVBO();

    void startSkyVBO(int list, int mode);
    void finishSkyVBO();

    void startSky2VBO(int list, int mode);
    void finishSky2VBO();


}
