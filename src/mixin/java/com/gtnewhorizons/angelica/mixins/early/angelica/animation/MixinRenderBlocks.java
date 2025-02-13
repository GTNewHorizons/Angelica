package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks implements ITexturesCache {

    @Shadow
    public IBlockAccess blockAccess;

    @Shadow
    public IIcon overrideBlockTexture;

    private Set<IIcon> renderedSprites = new ReferenceOpenHashSet<>();
    private boolean enableSpriteTracking;

    /**
     * @author laetansky Here where things get very tricky. We can't just mark blocks textures for update because this
     *         method gets called only when chunk render cache needs an update (that happens when a state of any block
     *         in that chunk changes). What we can do though is pass the rendered textures up to the WorldRenderer and
     *         later use it in RenderGlobal to mark textures for update and before that even sort WorldRenderers and
     *         apply Occlusion Querry (Basically that means that we will only mark those textures for update that are
     *         visible (on the viewport) at the moment)
     */
    @Inject(method = {
        "renderFaceYNeg",
        "renderFaceYPos",
        "renderFaceZNeg",
        "renderFaceZPos",
        "renderFaceXNeg",
        "renderFaceXPos"
    }, at = @At("HEAD"))
    public void angelica$beforeRenderFace(Block p_147761_1_, double p_147761_2_, double p_147761_4_,
            double p_147761_6_, IIcon icon, CallbackInfo ci) {
        if (overrideBlockTexture != null) {
            icon = overrideBlockTexture;
        }

        AnimationsRenderUtils.markBlockTextureForUpdate(icon, blockAccess);

        if(this.enableSpriteTracking) {
            this.renderedSprites.add(icon);
        }
    }

    @ModifyReturnValue(method = "getIconSafe", at = @At("RETURN"))
    public IIcon angelica$markBlockSideAnimationForUpdate(IIcon icon) {
        AnimationsRenderUtils.markBlockTextureForUpdate(icon, blockAccess);

        if(this.enableSpriteTracking) {
            this.renderedSprites.add(icon);
        }

        return icon;
    }

    @Override
    public Set<IIcon> getRenderedTextures() {
        return renderedSprites;
    }

    @Override
    public void enableTextureTracking() {
        enableSpriteTracking = true;
    }

    @Override
    public void track(IPatchedTextureAtlasSprite sprite) {
        if(this.enableSpriteTracking) {
            renderedSprites.add((IIcon) sprite);
        }
    }
}
