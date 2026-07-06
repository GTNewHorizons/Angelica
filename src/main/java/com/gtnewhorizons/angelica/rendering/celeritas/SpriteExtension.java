package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;

public interface SpriteExtension {
    SpriteTransparencyLevel celeritas$getTransparencyLevel();

    /** Marks sprite as active for animation. */
    void celeritas$markActive();

    /** One-shot: returns true if marked, then resets. */
    boolean celeritas$shouldUpdate();
}
