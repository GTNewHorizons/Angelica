package com.gtnewhorizons.angelica.mixins.late.client.campfirebackport;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

@Mixin(targets = "connor135246.campfirebackport.client.rendering.RenderBlockCampfire")
public class MixinRenderBlockCampfire {
    @Inject(method = "renderFace", at = @At("HEAD"))
    private static void angelica$beforeRenderFace(double x, double y, double z, Block block, RenderBlocks renderer, IIcon icon, int side, CallbackInfo ci) {
        AnimationsRenderUtils.markBlockTextureForUpdate(icon, renderer.blockAccess);
        ((ITexturesCache)renderer).getRenderedTextures().add(icon);
    }
    
    @ModifyArgs(method = "renderFire", at = @At(value = "INVOKE", target = "Lconnor135246/campfirebackport/client/rendering/RenderBlockCampfire;drawCrossedSquaresTwoIcons(Lnet/minecraft/util/IIcon;Lnet/minecraft/util/IIcon;DDDF)V"))
    private static void angelica$onDrawCrossedSquaresTwoIcons(Args args, double x, double y, double z, Block block, RenderBlocks renderer, boolean mix) {
        IIcon icon1 = (IIcon)args.get(0);
        AnimationsRenderUtils.markBlockTextureForUpdate(icon1, renderer.blockAccess);
        ((ITexturesCache)renderer).getRenderedTextures().add(icon1);
        
        IIcon icon2 = (IIcon)args.get(1);
        AnimationsRenderUtils.markBlockTextureForUpdate(icon2, renderer.blockAccess);
        ((ITexturesCache)renderer).getRenderedTextures().add(icon2);
    }
}
