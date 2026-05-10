package com.gtnewhorizons.angelica.stereo;

/**
 * How to render the 2D HUD when stereoscopic mode is active.
 */
public enum StereoHudMode {
    /** Draw the HUD twice, once into each eye's viewport. Recommended for SBS-3D viewers. */
    DUPLICATE,
    /** Draw the HUD once, full-screen. Looks stretched in SBS modes. Mainly useful for debugging. */
    STRETCH,
    /** Don't draw the HUD at all. */
    HIDE
}
