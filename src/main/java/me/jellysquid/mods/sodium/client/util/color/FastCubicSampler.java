package me.jellysquid.mods.sodium.client.util.color;

import cofh.lib.util.helpers.MathHelper;
import org.joml.Vector3d;

import java.util.function.Function;

import static org.joml.Math.lerp;

public class FastCubicSampler {
    private static final double[] DENSITY_CURVE = new double[] { 0.0D, 1.0D, 4.0D, 6.0D, 4.0D, 1.0D, 0.0D };
    private static final int DIAMETER = 6;

    private static Vector3d unpackRgb(int rgb) {
        return new Vector3d((rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0);
    }

    public static Vector3d sampleColor(Vector3d pos, ColorFetcher colorFetcher, Function<Vector3d, Vector3d> transformer) {
        int intX = MathHelper.floor(pos.x);
        int intY = MathHelper.floor(pos.y);
        int intZ = MathHelper.floor(pos.z);

        int[] values = new int[DIAMETER * DIAMETER * DIAMETER];

        for(int x = 0; x < DIAMETER; ++x) {
            int blockX = (intX - 2) + x;

            for(int y = 0; y < DIAMETER; ++y) {
                int blockY = (intY - 2) + y;

                for(int z = 0; z < DIAMETER; ++z) {
                    int blockZ = (intZ - 2) + z;

                    values[index(x, y, z)] = colorFetcher.fetch(blockX, blockY, blockZ);
                }
            }
        }

        // Fast path! Skip blending the colors if all inputs are the same
        if (isHomogenousArray(values)) {
            // Take the first color if it's homogenous (all elements are the same...)
            return transformer.apply(unpackRgb(values[0]));
        }

        double deltaX = pos.x - (double)intX;
        double deltaY = pos.y - (double)intY;
        double deltaZ = pos.z - (double)intZ;

        Vector3d sum = new Vector3d();
        double totalFactor = 0.0D;

        for(int x = 0; x < DIAMETER; ++x) {
            double densityX = lerp(deltaX, DENSITY_CURVE[x + 1], DENSITY_CURVE[x]);

            for(int y = 0; y < DIAMETER; ++y) {
                double densityY = lerp(deltaY, DENSITY_CURVE[y + 1], DENSITY_CURVE[y]);

                for(int z = 0; z < DIAMETER; ++z) {
                    double densityZ = lerp(deltaZ, DENSITY_CURVE[z + 1], DENSITY_CURVE[z]);

                    double factor = densityX * densityY * densityZ;
                    totalFactor += factor;

                    Vector3d color = transformer.apply(unpackRgb(values[index(x, y, z)]));
                    sum.add(color.mul(factor));
                }
            }
        }

        sum.mul(1.0D / totalFactor);

        return sum;
    }

    private static int index(int x, int y, int z) {
        return (DIAMETER * DIAMETER * z) + (DIAMETER * y) + x;
    }

    public interface ColorFetcher {
        int fetch(int x, int y, int z);
    }

    private static boolean isHomogenousArray(int[] arr) {
        int val = arr[0];

        for (int i = 1; i < arr.length; i++) {
            if (arr[i] != val) {
                return false;
            }
        }

        return true;
    }
}
