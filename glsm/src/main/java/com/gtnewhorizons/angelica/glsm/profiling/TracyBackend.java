package com.gtnewhorizons.angelica.glsm.profiling;

public interface TracyBackend {
    int PLOT_FORMAT_NUMBER = 0;
    int PLOT_FORMAT_MEMORY = 1;
    int PLOT_FORMAT_PERCENTAGE = 2;

    boolean init();

    long internSrcLoc(String name, int color);
    long dynamicSrcLoc();

    long beginZone(long srcLoc);
    void endZone(long ctx);

    void zoneText(long ctx, String text);
    void zoneValue(long ctx, long value);

    void frameMark();
    void frameMark(long namePtr);

    long internFrameName(String name);
    long internPlotName(String name, int format);

    void plotInt(long namePtr, long value);
    void plot(long namePtr, double value);
    void message(String text);
    void setCurrentThreadName(String name);
    boolean isConnected();
    void shutdown();

    boolean gpuInit();
    boolean gpuBeginZone(long srcLoc);
    void gpuEndZone();
    void gpuCollect();
}
