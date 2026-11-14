package com.gtnewhorizons.angelica.api.shadows;

public enum ShadowStage {
    /** After the shadow pass's entities and tile entities, before translucent depth is split off. */
    SOLID,
    /** After translucent terrain. Only runs when the pack draws translucent shadows. */
    TRANSLUCENT
}
