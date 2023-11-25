package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.texture.SpriteExtended;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite implements SpriteExtended {
    private boolean forceNextUpdate;

    @Shadow
    protected List<int[][]> framesTextureData;

    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Shadow
    protected int originX;

    @Shadow
    protected int originY;

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    protected int frameCounter;

    @Shadow
    protected int tickCounter;

    /**
     * @author JellySquid & Asek3
     * @reason To optimal solution it's better to overwrite
     */
    @Overwrite
    public void updateAnimation() {
        ++this.tickCounter;

        boolean onDemand = SodiumClientMod.options().advanced.animateOnlyVisibleTextures;

        if (!onDemand || this.forceNextUpdate) {
            this.uploadTexture();
        }
    }

    private void uploadTexture() {
        if (this.tickCounter >= this.animationMetadata.getFrameTimeSingle(this.frameCounter)) {
            int prevFrameIndex = this.animationMetadata.getFrameIndex(this.frameCounter);
            int frameCount = this.animationMetadata.getFrameCount() == 0 ? this.framesTextureData.size() : this.animationMetadata.getFrameCount();

            this.frameCounter = (this.frameCounter + 1) % frameCount;
            this.tickCounter = 0;

            int frameIndex = this.animationMetadata.getFrameIndex(this.frameCounter);

            if (prevFrameIndex != frameIndex && frameIndex >= 0 && frameIndex < this.framesTextureData.size()) {
                TextureUtil.uploadTextureMipmap(this.framesTextureData.get(frameIndex), this.width, this.height, this.originX, this.originY, false, false);
            }
        }

        this.forceNextUpdate = false;
    }

    @Override
    public void markActive() {
        this.forceNextUpdate = true;
    }
}
