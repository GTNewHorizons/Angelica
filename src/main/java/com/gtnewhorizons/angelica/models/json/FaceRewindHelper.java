package com.gtnewhorizons.angelica.models.json;

/**
 * Modern vanilla uses magic numbers to rewind quads, let's try that. NOTE: may or may not work with sheared quads, but
 * probably not.
 */
public class FaceRewindHelper {
    /**
     * These constants map face sides to the characteristics of the first vertex. False is min, true is max, and the
     * order is XYZ. So the first vertex of DOWN faces should have the minimum X and Y and the maximum Z.
     */
    public static final boolean[] DOWN = { false, false, true };
    public static final boolean[] UP = { false, true, false };
    public static final boolean[] NORTH = { true, true, false };
    public static final boolean[] SOUTH = { false, true, true };
    public static final boolean[] WEST = { false, true, false };
    public static final boolean[] EAST = { true, true, true };
}
