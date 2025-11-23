package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.RenderBlockFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderBlockFluid.class, remap = false)
public abstract class MixinRenderBlockFluid {

    @Unique
    private IBlockAccess currentBlockAccess;

    @Inject(method = "renderWorldBlock", at = @At(value = "HEAD"))
    private void angelica$saveCurrentBlockAccess(IBlockAccess world, int x, int y, int z, Block block, int modelId,
            RenderBlocks renderer, CallbackInfoReturnable<Boolean> cir) {
        currentBlockAccess = world;
    }

    /**
     * @author laetansky
     * @reason mark texture for update
     */
    @Inject(method = "getIcon", at = @At(value = "RETURN", ordinal = 0))
    private void angelica$updateTexture(IIcon icon, CallbackInfoReturnable<IIcon> cir) {
        AnimationsRenderUtils.markBlockTextureForUpdate(icon, currentBlockAccess);
    }
}
