package com.gtnewhorizons.angelica.stereo;

/**
 * Stereoscopic output layout. SBS modes split the screen left/right; OU modes split top/bottom.
 *
 * <p>HALF variants render each eye at half the screen dimension along the split axis at
 * native aspect — preferred for VR virtual monitor / headset SBS-3D viewers.</p>
 *
 * <p>FULL variants render each eye at full screen dimension along the split axis (squished),
 * matching the legacy "frame-packed" expectation. Generally avoid unless you have a specific
 * display that wants this layout.</p>
 */
public enum StereoMode {
    OFF,
    SBS_HALF,
    SBS_FULL,
    OU_HALF,
    OU_FULL;

    public boolean isActive() {
        return this != OFF;
    }

    public boolean isSideBySide() {
        return this == SBS_HALF || this == SBS_FULL;
    }

    public boolean isOverUnder() {
        return this == OU_HALF || this == OU_FULL;
    }

    /**
     * @return true if each eye is rendered at half the split-axis dimension (preserving aspect)
     */
    public boolean isHalf() {
        return this == SBS_HALF || this == OU_HALF;
    }
}
