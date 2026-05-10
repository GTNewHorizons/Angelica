package com.gtnewhorizons.angelica.stereo;

/**
 * Debug-only override for which eye is "active" while validating the stereo camera offset
 * before the two-pass loop is wired up. Removed at step 3 of the SBS implementation.
 */
public enum StereoDebugEye {
    OFF,
    LEFT,
    RIGHT
}
