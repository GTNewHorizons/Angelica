package com.gtnewhorizons.angelica.mixins.interfaces;

@SuppressWarnings("unused")
public interface IPatchedTextureAtlasSprite {
    void angelica$markNeedsAnimationUpdate();
    boolean angelica$needsAnimationUpdate();
    void angelica$unmarkNeedsAnimationUpdate();
    boolean angelica$tickAnimation(boolean onlyVisible);
}
