package com.gtnewhorizons.angelica.glsm.profiling;

public interface TracyBackend {
    int PLOT_FORMAT_NUMBER = 0;
    int PLOT_FORMAT_MEMORY = 1;
    int PLOT_FORMAT_PERCENTAGE = 2;

    int SEVERITY_TRACE = 0;
    int SEVERITY_DEBUG = 1;
    int SEVERITY_INFO = 2;
    int SEVERITY_WARNING = 3;
    int SEVERITY_ERROR = 4;
    int SEVERITY_FATAL = 5;

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

    void message(String text, int severity);
    default void message(String text) { message(text, SEVERITY_INFO); }

    long sectionEnter(int category, String text);
    void sectionLeave(long id);
    void sectionSetup(int category, String name);

    void setCurrentThreadName(String name);
    boolean isConnected();
    void shutdown();

    boolean gpuInit();
    boolean gpuBeginZone(long srcLoc);
    void gpuEndZone();
    void gpuCollect();
}
