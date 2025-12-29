package com.gtnewhorizons.angelica.mixins.interfaces;

@SuppressWarnings("unused")
public interface IPatchedTextureAtlasSprite {
    void markNeedsAnimationUpdate();
    boolean needsAnimationUpdate();
    void unmarkNeedsAnimationUpdate();
    void updateAnimationsDryRun();
}
