package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Provides animation active-state tracking and dry-run updates. */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements IPatchedTextureAtlasSprite {

    @Shadow protected int tickCounter;
    @Shadow protected int frameCounter;
    @Shadow private AnimationMetadataSection animationMetadata;
    @Shadow protected List<?> framesTextureData;

    @Unique private boolean angelica$isActive = false;

    @Override
    public void markNeedsAnimationUpdate() {
        this.angelica$isActive = true;
    }

    @Override
    public boolean needsAnimationUpdate() {
        if (this.angelica$isActive) {
            this.angelica$isActive = false;
            return true;
        }
        return false;
    }

    @Override
    public void unmarkNeedsAnimationUpdate() {
        this.angelica$isActive = false;
    }

    @Override
    public void updateAnimationsDryRun() {
        if (animationMetadata == null || framesTextureData == null) return;

        tickCounter++;
        if (tickCounter >= animationMetadata.getFrameTimeSingle(frameCounter)) {
            int j = this.animationMetadata.getFrameCount() == 0 ? framesTextureData.size()
                    : this.animationMetadata.getFrameCount();
            this.frameCounter = (this.frameCounter + 1) % j;
            this.tickCounter = 0;
        }
    }
}
