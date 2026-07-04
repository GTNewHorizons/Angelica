package com.gtnewhorizons.angelica.rendering.celeritas;

public final class TeDistanceMath {
    private TeDistanceMath() {}

    public static double distSqToSection(double camX, double camY, double camZ, int ox, int oy, int oz) {
        final double dx = axisDist(camX, ox);
        final double dy = axisDist(camY, oy);
        final double dz = axisDist(camZ, oz);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDist(double cam, int min) {
        if (cam < min) return min - cam;
        final double max = min + 16;
        if (cam > max) return cam - max;
        return 0;
    }
}
