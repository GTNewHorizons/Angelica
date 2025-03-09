package org.taumc.celeritas.impl.extensions;

import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;

public interface SpriteExtension {
    void celeritas$markActive();
    boolean celeritas$shouldUpdate();
    SpriteTransparencyLevel celeritas$getTransparencyLevel();
}
