package com.gtnewhorizons.angelica.glsm.ffp;

/**
 * The per-frame half of the rain and snow instancing, set once per weather draw.
 */
public final class WeatherParams {

    public static float translateX;
    public static float translateY;
    public static float translateZ;
    public static float invRadius;

    public static float cameraFracX;
    public static float cameraFracZ;
    public static float partialTicks;
    public static float age;

    public static float rainScroll;
    public static float snowScroll;
    public static float rainStrength;

    public static int generation;

    private WeatherParams() {}

    public static void set(float translateX, float translateY, float translateZ, float invRadius,
        float cameraFracX, float cameraFracZ, float partialTicks, float age,
        float rainScroll, float snowScroll, float rainStrength) {
        WeatherParams.translateX = translateX;
        WeatherParams.translateY = translateY;
        WeatherParams.translateZ = translateZ;
        WeatherParams.invRadius = invRadius;
        WeatherParams.cameraFracX = cameraFracX;
        WeatherParams.cameraFracZ = cameraFracZ;
        WeatherParams.partialTicks = partialTicks;
        WeatherParams.age = age;
        WeatherParams.rainScroll = rainScroll;
        WeatherParams.snowScroll = snowScroll;
        WeatherParams.rainStrength = rainStrength;
        generation++;
    }
}
