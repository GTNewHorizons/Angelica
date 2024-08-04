package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.base;

import net.minecraft.block.Block;
import net.minecraft.block.BlockMycelium;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;

@Mixin(BlockMycelium.class)
public abstract class MixinBlockMycelium {

    @Shadow
    private IIcon field_150200_a;

    @Inject(
        method = "getIcon(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
        at = @At("HEAD"),
        cancellable = true)
    private void modifyGetIcon(IBlockAccess worldIn, int x, int y, int z, int side, CallbackInfoReturnable<IIcon> cir) {
        final IIcon grassTexture = RenderBlocksUtils
            .getGrassTexture((Block) (Object) this, worldIn, x, y, z, side, this.field_150200_a);
        if (grassTexture != null) {
            cir.setReturnValue(grassTexture);
        }
    }
}
