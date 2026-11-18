package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import net.coderbot.iris.texture.pbr.PBRSpriteHolder;
import net.coderbot.iris.texture.pbr.TextureAtlasSpriteExtension;
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
    @Shadow public abstract void updateAnimation();

    @Unique private boolean angelica$isActive = false;

    @Override
    public void angelica$markNeedsAnimationUpdate() {
        this.angelica$isActive = true;
        AnimationsRenderUtils.recordSpriteUsage(this);
    }

    @Override
    public boolean angelica$needsAnimationUpdate() {
        if (this.angelica$isActive) {
            this.angelica$isActive = false;
            // PBR sprites are never UV-sampled; they inherit visibility when the base sprite's mark is consumed
            if (this instanceof TextureAtlasSpriteExtension extension) {
                final PBRSpriteHolder pbrHolder = extension.getPBRHolder();
                if (pbrHolder != null) {
                    final TextureAtlasSprite normalSprite = pbrHolder.getNormalSprite();
                    final TextureAtlasSprite specularSprite = pbrHolder.getSpecularSprite();
                    if (normalSprite != null) ((IPatchedTextureAtlasSprite) normalSprite).angelica$markNeedsAnimationUpdate();
                    if (specularSprite != null) ((IPatchedTextureAtlasSprite) specularSprite).angelica$markNeedsAnimationUpdate();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void angelica$unmarkNeedsAnimationUpdate() {
        this.angelica$isActive = false;
    }

    @Override
    public boolean angelica$tickAnimation(boolean onlyVisible) {
        if (!onlyVisible || angelica$needsAnimationUpdate()) {
            updateAnimation();
            return true;
        }
        angelica$advanceFramesDryRun();
        return false;
    }

    @Unique
    private void angelica$advanceFramesDryRun() {
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
