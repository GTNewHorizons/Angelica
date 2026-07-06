package com.gtnewhorizons.angelica.dynamiclights;

import org.jetbrains.annotations.NotNull;

/**
 * Calculates the appropriate tick mode for a dynamic light source based on distance from camera and position relative to camera direction.
 */
public final class AdaptiveTickCalculator {

    // Distance thresholds in blocks (configurable via settings)
    private static int slowDistance = 32;       // ~2 chunks
    private static int slowerDistance = 64;     // ~4 chunks
    private static int backgroundDistance = 128; // ~8 chunks

    // Squared thresholds for efficient distance comparison
    private static int slowDistanceSq = slowDistance * slowDistance;
    private static int slowerDistanceSq = slowerDistance * slowerDistance;
    private static int backgroundDistanceSq = backgroundDistance * backgroundDistance;

    private AdaptiveTickCalculator() {}

    /**
     * Calculate the appropriate tick mode for a light source.
     */
    public static AdaptiveTickMode calculate(@NotNull IDynamicLightSource source, double cameraX, double cameraY, double cameraZ, double lookDirX, double lookDirZ) {
        double dx = source.angelica$getDynamicLightX() - cameraX;
        double dy = source.angelica$getDynamicLightY() - cameraY;
        double dz = source.angelica$getDynamicLightZ() - cameraZ;

        double distSq = dx * dx + dy * dy + dz * dz;

        // Check if behind camera (dot product of direction to source with look direction)
        // Only use X and Z for horizontal check (more stable as player looks up/down)
        double dot = dx * lookDirX + dz * lookDirZ;
        boolean behindCamera = dot < 0;

        // Behind camera always gets BACKGROUND mode
        if (behindCamera) {
            return AdaptiveTickMode.BACKGROUND;
        }

        // Distance-based mode selection
        if (distSq > backgroundDistanceSq) {
            return AdaptiveTickMode.BACKGROUND;
        }
        if (distSq > slowerDistanceSq) {
            return AdaptiveTickMode.SLOWER;
        }
        if (distSq > slowDistanceSq) {
            return AdaptiveTickMode.SLOW;
        }

        return AdaptiveTickMode.REAL_TIME;
    }

    public static void setSlowDistance(int distance) {
        slowDistance = distance;
        slowDistanceSq = distance * distance;
    }

    public static void setSlowerDistance(int distance) {
        slowerDistance = distance;
        slowerDistanceSq = distance * distance;
    }

    public static void setBackgroundDistance(int distance) {
        backgroundDistance = distance;
        backgroundDistanceSq = distance * distance;
    }
}
