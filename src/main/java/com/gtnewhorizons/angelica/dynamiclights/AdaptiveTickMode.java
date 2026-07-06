package com.gtnewhorizons.angelica.dynamiclights;

/**
 * Defines tick modes for adaptive dynamic light updates. Distant or off-screen light sources update less frequently to save CPU.
 */
public enum AdaptiveTickMode {
    /** Updates every tick - for nearby, visible light sources */
    REAL_TIME(1),
    /** Updates every 5 ticks - for light sources at moderate distance */
    SLOW(5),
    /** Updates every 10 ticks - for light sources at far distance */
    SLOWER(10),
    /** Updates every 20 ticks - for light sources behind the camera or very far */
    BACKGROUND(20);

    private final int delay;

    AdaptiveTickMode(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public boolean shouldTickThisFrame(int worldTick, int sourceHash) {
        if (delay <= 1) {
            return true;
        }
        return (worldTick % delay) == Math.abs(sourceHash % delay);
    }
}
