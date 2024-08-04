package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import com.gtnewhorizons.angelica.mixins.interfaces.AnimationMetadataSectionAccessor;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimationMetadataSection.class)
public abstract class MixinAnimationMetadataSection implements AnimationMetadataSectionAccessor {
    @Accessor("frameWidth")
    public abstract int getFrameWidth();

    @Mutable
    @Accessor("frameWidth")
    public abstract void setFrameWidth(int frameWidth);

    @Accessor("frameHeight")
    public abstract int getFrameHeight();

    @Mutable
    @Accessor("frameHeight")
    public abstract void setFrameHeight(int frameHeight);
}
