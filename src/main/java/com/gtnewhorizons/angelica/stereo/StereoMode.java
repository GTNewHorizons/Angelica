package com.gtnewhorizons.angelica.stereo;

/**
 * Stereoscopic output layout. SBS splits left/right; OU splits top/bottom. HALF variants render
 * each eye at half the split-axis dimension (native aspect) — what SBS-3D viewers expect. FULL
 * variants render at the full split-axis dimension (squished), for legacy frame-packed displays.
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

    public boolean isHalf() {
        return this == SBS_HALF || this == OU_HALF;
    }
}
