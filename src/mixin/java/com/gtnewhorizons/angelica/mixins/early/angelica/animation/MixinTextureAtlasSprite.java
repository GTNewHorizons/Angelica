package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements IPatchedTextureAtlasSprite {

    @Unique
    private boolean needsAnimationUpdate = false;

    @Shadow
    protected int tickCounter;
    @Shadow
    protected int frameCounter;

    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Shadow
    protected List<?> framesTextureData;

    @Override
    public void markNeedsAnimationUpdate() {
        needsAnimationUpdate = true;
    }

    @Override
    public boolean needsAnimationUpdate() {
        return needsAnimationUpdate;
    }

    @Override
    public void unmarkNeedsAnimationUpdate() {
        needsAnimationUpdate = false;
    }

    @Override
    public void updateAnimationsDryRun() {
        // account for weird subclass that doesn't use the stock mechanisms for animation
        if (animationMetadata == null || framesTextureData == null) return;

        tickCounter++;
        if (tickCounter >= animationMetadata.getFrameTimeSingle(frameCounter)) {
            int j = this.animationMetadata.getFrameCount() == 0 ? framesTextureData.size()
                    : this.animationMetadata.getFrameCount();
            this.frameCounter = (this.frameCounter + 1) % j;
            this.tickCounter = 0;
        }
    }

    @ModifyReturnValue(method = "getMinU", at = @At("RETURN"))
    private float angelica$onUVAccessed(float value) {
        if (((ISpriteExt)this).isAnimation()) {
            AnimationsRenderUtils.onSpriteUsed(this);
            needsAnimationUpdate = true;
        }
        return value;
    }
}
