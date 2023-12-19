package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cc.ColorizeBlock;

// TODO: split ctm // cc use and migrate to MixinRenderBlocks & MixinRenderBlocksCC
@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;
    @Shadow
    public boolean enableAO;

    @Shadow
    public abstract IIcon getBlockIcon(Block block, IBlockAccess access, int x, int y, int z, int side);

    @Shadow
    public abstract IIcon getBlockIconFromSideAndMetadata(Block block, int side, int meta);

    @Unique
    private int mcpatcherforge$neededSideRenderBlockLiquid;

    @Unique
    private float mcpatcherforge$neededFloat1;

    @Unique
    private float mcpatcherforge$neededFloat2;

    @Unique
    private float mcpatcherforge$neededFloat3;

    // Redirect calls to this.getBlockIcon when possible

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
            ordinal = 0))
    private IIcon mcpatcherforge$obtainFloatsAndRedirectToGetBlockIcon(RenderBlocks instance, Block block, int side,
        int meta, Block specializedBlock, int x, int y, int z) {
        int l = block.colorMultiplier(this.blockAccess, x, y, z);
        this.mcpatcherforge$neededFloat1 = (float) (l >> 16 & 255) / 255.0F;
        this.mcpatcherforge$neededFloat2 = (float) (l >> 8 & 255) / 255.0F;
        this.mcpatcherforge$neededFloat3 = (float) (l & 255) / 255.0F;
        return (this.blockAccess == null) ? this.getBlockIconFromSideAndMetadata(block, side, meta)
            : this.getBlockIcon(block, this.blockAccess, x, y, z, side);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 2))
    private void mcpatcherforge$redirectColor10(Tessellator tessellator, float red, float green, float blue,
        Block block, int x, int y, int z) {
        if (!(ColorizeBlock.isSmooth = ColorizeBlock.setupBlockSmoothing(
            (RenderBlocks) (Object) this,
            block,
            this.blockAccess,
            x,
            y,
            z,
            this.mcpatcherforge$neededSideRenderBlockLiquid + 6))) {
            tessellator.setColorOpaque_F(red, green, blue);
        }
    }

    // Capture needed value
    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
            ordinal = 2))
    private IIcon mcpatcherforge$saveSideAndRedirectToGetBlockIcon(RenderBlocks instance, Block block, int side,
        int meta, Block specializedBlock, int x, int y, int z) {
        this.mcpatcherforge$neededSideRenderBlockLiquid = side;
        return (this.blockAccess == null) ? this.getBlockIconFromSideAndMetadata(block, side, meta)
            : this.getBlockIcon(block, this.blockAccess, x, y, z, side);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 1))
    private void mcpatcherforge$redirectColor9(Tessellator tessellator, float red, float green, float blue, Block block,
        int x, int y, int z) {
        if (!(ColorizeBlock.isSmooth = ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, x, y, z, 6))) {
            tessellator.setColorOpaque_F(
                red * this.mcpatcherforge$neededFloat1,
                green * this.mcpatcherforge$neededFloat2,
                blue * this.mcpatcherforge$neededFloat3);
        }
        if (ColorizeBlock.isSmooth) {
            this.enableAO = true;
        }
    }
}
