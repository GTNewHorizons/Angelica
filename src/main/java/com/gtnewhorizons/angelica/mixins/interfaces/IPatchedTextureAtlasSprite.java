package com.gtnewhorizons.angelica.mixins.interfaces;

public interface IPatchedTextureAtlasSprite {

    void markNeedsAnimationUpdate();

    boolean needsAnimationUpdate();

    void unmarkNeedsAnimationUpdate();

    void updateAnimationsDryRun();
}
