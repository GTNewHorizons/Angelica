package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityFX.class)
public class MixinEntityFX {

    @Shadow
    protected IIcon particleIcon;

    private boolean shouldTickSprite;

    @Inject(method = "setParticleIcon", at = @At("RETURN"))
    public void afterSetSprite(IIcon icon, CallbackInfo ci) {
        this.shouldTickSprite = icon instanceof ISpriteExt atlasSprite && atlasSprite.isAnimation();
    }

    @Inject(method = "renderParticle", at = @At("HEAD"))
    public void renderParticle(Tessellator tessellator, float offset, float x, float y, float z, float u, float v, CallbackInfo ci) {
        if (this.shouldTickSprite && this.particleIcon instanceof TextureAtlasSprite atlasSprite) {
            SpriteUtil.markSpriteActive(atlasSprite);
        }
    }

}
