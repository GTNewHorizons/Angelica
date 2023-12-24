package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer implements ITexturesCache {

    @Shadow
    public boolean isInFrustum;

    @Unique
    private Set<IIcon> renderedIcons;

    @ModifyArg(method = "updateRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;<init>(Lnet/minecraft/world/IBlockAccess;)V"))
    private IBlockAccess hodgepodge$onUpdateRenderer(IBlockAccess chunkCache) {
        renderedIcons = ((ITexturesCache) chunkCache).getRenderedTextures();
        return chunkCache;
    }

    @Inject(method = "getGLCallListForPass", at = @At("HEAD"))
    private void hodgepodge$getGLCallListForPass(int pass, CallbackInfoReturnable<Integer> cir) {
        if (isInFrustum && pass == 0 && renderedIcons != null) {
            for (IIcon icon : renderedIcons) {
                ((IPatchedTextureAtlasSprite) icon).markNeedsAnimationUpdate();
            }
        }
    }

    @Override
    public Set<IIcon> getRenderedTextures() {
        return renderedIcons;
    }
}
