package com.gtnewhorizons.angelica.mixins.interfaces;

import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

public interface AnimationMetadataSectionAccessor {
    int getFrameWidth();

    void setFrameWidth(int frameWidth);

    int getFrameHeight();

    void setFrameHeight(int frameHeight);
}
