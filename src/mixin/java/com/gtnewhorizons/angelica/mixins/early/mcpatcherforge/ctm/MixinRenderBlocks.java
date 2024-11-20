package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.ctm.GlassPaneRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;

    @Shadow
    public IIcon overrideBlockTexture;

    @Shadow
    public abstract IIcon getBlockIcon(Block block, IBlockAccess access, int x, int y, int z, int side);

    @Shadow
    public abstract IIcon getBlockIconFromSideAndMetadata(Block block, int side, int meta);

    @Shadow
    public abstract IIcon getBlockIconFromSide(Block block, int side);

    @Shadow
    public abstract boolean hasOverrideBlockTexture();

    @Shadow
    public abstract IIcon getIconSafe(IIcon texture);

    @Redirect(
        method = "renderBlockMinecartTrack(Lnet/minecraft/block/BlockRailBase;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockMinecartTrack(RenderBlocks instance, Block block, int side, int meta,
        BlockRailBase specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = {
            "renderBlockVine(Lnet/minecraft/block/Block;III)Z",
            "renderBlockLilyPad(Lnet/minecraft/block/Block;III)Z",
            "renderBlockLadder(Lnet/minecraft/block/Block;III)Z",
            "renderBlockTripWireSource(Lnet/minecraft/block/Block;III)Z",
            "renderBlockLever(Lnet/minecraft/block/Block;III)Z",
            "renderBlockTripWire(Lnet/minecraft/block/Block;III)Z"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSide(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;"))
    private IIcon redirectGetBlockIconFromSide(RenderBlocks instance, Block block, int side, Block specializedBlock,
        int x, int y, int z) {
        return getBlockIcon(block, blockAccess, x, y, z, side);
    }

    @Redirect(
        method = "renderBlockBrewingStand(Lnet/minecraft/block/BlockBrewingStand;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockBrewingStand(RenderBlocks instance, Block block, int side, int meta,
        BlockBrewingStand specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "renderBlockFlowerpot(Lnet/minecraft/block/BlockFlowerPot;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSide(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockFlowerpot(RenderBlocks instance, Block block, int side,
        BlockFlowerPot specializedBlock, int x, int y, int z) {
        return getBlockIcon(block, blockAccess, x, y, z, side);
    }

    @Redirect(
        method = "renderBlockAnvilRotate(Lnet/minecraft/block/BlockAnvil;IIIIFFFFZZI)F",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockAnvilRotate(RenderBlocks instance, Block block, int side, int meta,
        BlockAnvil specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "renderBlockRedstoneDiodeMetadata(Lnet/minecraft/block/BlockRedstoneDiode;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderRedstoneDiodeMetadata(RenderBlocks instance, Block block, int side, int meta,
        BlockRedstoneDiode specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }


    @Redirect(method = "renderBlockPane", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon tweakPaneIcons(RenderBlocks instance, Block block, int side, int meta) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    /**
     * Originally an overwrite with the reason of "significant deviations from vanilla"
     * Refactored by Roadhog360 to use injects instead for compatibility reasons
     *
     * @author mist475
     * @author roadhog360
     */
    @SuppressWarnings("UnreachableCode")
    @Inject(method = "renderBlockPane", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockPane;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z", ordinal = 1, shift = At.Shift.BY, by = 2), cancellable = true)
    private void tweakPaneRenderer(BlockPane block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir,
                                   @Local(name = "iicon") IIcon iicon, @Local(name = "l") int l,
                                   @Local(name = "d21") double d21, @Local(name = "d0") double d0, @Local(name = "d1") double d1, @Local(name = "d2") double d2,
                                   @Local(name = "d3") double d3, @Local(name = "d4") double d4, @Local(name = "d5") double d5, @Local(name = "d6") double d6,
                                   @Local(name = "d7") double d7, @Local(name = "d8") double d8, @Local(name = "d9") double d9, @Local(name = "d10") double d10,
                                   @Local(name = "d11") double d11, @Local(name = "d12") double d12, @Local(name = "d13") double d13, @Local(name = "d14") double d14,
                                   @Local(name = "d15") double d15, @Local(name = "d16") double d16, @Local(name = "d17") double d17, @Local(name = "d18") double d18,
                                   @Local(name = "flag") boolean flag, @Local(name = "flag1") boolean flag1,
                                   @Local(name = "flag2") boolean flag2, @Local(name = "flag3") boolean flag3,
                                   @Local(name = "flag4") boolean flag4, @Local(name = "flag5") boolean flag5) {
        Tessellator tessellator = Tessellator.instance;
        GlassPaneRenderer.renderThin((RenderBlocks) (Object) this, block, iicon, x, y, z, flag, flag1, flag2, flag3);
        if ((flag2 && flag3) || (!flag2 && !flag3 && !flag && !flag1)) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(x, y + 1, d13, d21, d2);
                tessellator.addVertexWithUV(x, y, d13, d21, d3);
                tessellator.addVertexWithUV(d11, y, d13, d1, d3);
                tessellator.addVertexWithUV(d11, y + 1, d13, d1, d2);
                tessellator.addVertexWithUV(d11, y + 1, d13, d21, d2);
                tessellator.addVertexWithUV(d11, y, d13, d21, d3);
                tessellator.addVertexWithUV(x, y, d13, d1, d3);
                tessellator.addVertexWithUV(x, y + 1, d13, d1, d2);
            }
            if (flag4) {
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d6);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d6);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d8);
                }
            } else {
                if (y < l - 1 && this.blockAccess.isAirBlock(x - 1, y + 1, z)
                    && !GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d7);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d7);
                }
                if (y < l - 1 && this.blockAccess.isAirBlock(x + 1, y + 1, z)
                    && !GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d7);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d6);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d7);
                    tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d6);
                }
            }
            if (flag5) {
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d6);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d6);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d6);
                    tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d8);
                }
            } else {
                if (y > 1 && this.blockAccess.isAirBlock(x - 1, y - 1, z)
                    && !GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d7);
                    tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d7);
                }
                if (y > 1 && this.blockAccess.isAirBlock(x + 1, y - 1, z)) {
                    if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                        tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d6);
                        tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d7);
                        tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d7);
                        tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d6);
                        tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d6);
                        tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d7);
                        tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d7);
                        tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d6);
                    }
                }
            }
        } else if (flag2) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(x, y + 1, d13, d21, d2);
                tessellator.addVertexWithUV(x, y, d13, d21, d3);
                tessellator.addVertexWithUV(d10, y, d13, d0, d3);
                tessellator.addVertexWithUV(d10, y + 1, d13, d0, d2);
                tessellator.addVertexWithUV(d10, y + 1, d13, d21, d2);
                tessellator.addVertexWithUV(d10, y, d13, d21, d3);
                tessellator.addVertexWithUV(x, y, d13, d0, d3);
                tessellator.addVertexWithUV(x, y + 1, d13, d0, d2);
            }
            if (!flag1 && !flag) {
                tessellator.addVertexWithUV(d10, y + 1, d18, d4, d6);
                tessellator.addVertexWithUV(d10, y, d18, d4, d8);
                tessellator.addVertexWithUV(d10, y, d17, d5, d8);
                tessellator.addVertexWithUV(d10, y + 1, d17, d5, d6);
                tessellator.addVertexWithUV(d10, y + 1, d17, d4, d6);
                tessellator.addVertexWithUV(d10, y, d17, d4, d8);
                tessellator.addVertexWithUV(d10, y, d18, d5, d8);
                tessellator.addVertexWithUV(d10, y + 1, d18, d5, d6);
            }
            if ((flag4 || (y < l - 1 && this.blockAccess.isAirBlock(x - 1, y + 1, z)))
                && !GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d8);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d8);
                tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d7);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(x, y + 1 + 0.01, d18, d5, d8);
                tessellator.addVertexWithUV(x, y + 1 + 0.01, d17, d4, d8);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d7);
            }
            if (flag5 || (y > 1 && this.blockAccess.isAirBlock(x - 1, y - 1, z))) {
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d7);
                    tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d7);
                    tessellator.addVertexWithUV(x, y - 0.01, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y - 0.01, d17, d4, d8);
                    tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d7);
                }
            }
        } else if (flag3) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d10, y + 1, d13, d0, d2);
                tessellator.addVertexWithUV(d10, y, d13, d0, d3);
                tessellator.addVertexWithUV(d11, y, d13, d1, d3);
                tessellator.addVertexWithUV(d11, y + 1, d13, d1, d2);
                tessellator.addVertexWithUV(d11, y + 1, d13, d0, d2);
                tessellator.addVertexWithUV(d11, y, d13, d0, d3);
                tessellator.addVertexWithUV(d10, y, d13, d1, d3);
                tessellator.addVertexWithUV(d10, y + 1, d13, d1, d2);
            }
            if (!flag1 && !flag) {
                tessellator.addVertexWithUV(d10, y + 1, d17, d4, d6);
                tessellator.addVertexWithUV(d10, y, d17, d4, d8);
                tessellator.addVertexWithUV(d10, y, d18, d5, d8);
                tessellator.addVertexWithUV(d10, y + 1, d18, d5, d6);
                tessellator.addVertexWithUV(d10, y + 1, d18, d4, d6);
                tessellator.addVertexWithUV(d10, y, d18, d4, d8);
                tessellator.addVertexWithUV(d10, y, d17, d5, d8);
                tessellator.addVertexWithUV(d10, y + 1, d17, d5, d6);
            }
            if ((flag4 || (y < l - 1 && this.blockAccess.isAirBlock(x + 1, y + 1, z)))
                && !GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d6);
                tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d7);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d6);
                tessellator.addVertexWithUV(d11, y + 1 + 0.01, d18, d5, d6);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(d10, y + 1 + 0.01, d17, d4, d7);
                tessellator.addVertexWithUV(d11, y + 1 + 0.01, d17, d4, d6);
            }
            if ((flag5 || (y > 1 && this.blockAccess.isAirBlock(x + 1, y - 1, z)))
                && !GlassPaneRenderer.skipBottomEdgeRendering) {
                tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d6);
                tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d7);
                tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d6);
                tessellator.addVertexWithUV(d11, y - 0.01, d18, d5, d6);
                tessellator.addVertexWithUV(d10, y - 0.01, d18, d5, d7);
                tessellator.addVertexWithUV(d10, y - 0.01, d17, d4, d7);
                tessellator.addVertexWithUV(d11, y - 0.01, d17, d4, d6);
            }
        }
        if ((flag && flag1) || (!flag2 && !flag3 && !flag && !flag1)) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d10, y + 1, d14, d21, d2);
                tessellator.addVertexWithUV(d10, y, d14, d21, d3);
                tessellator.addVertexWithUV(d10, y, z, d1, d3);
                tessellator.addVertexWithUV(d10, y + 1, z, d1, d2);
                tessellator.addVertexWithUV(d10, y + 1, z, d21, d2);
                tessellator.addVertexWithUV(d10, y, z, d21, d3);
                tessellator.addVertexWithUV(d10, y, d14, d1, d3);
                tessellator.addVertexWithUV(d10, y + 1, d14, d1, d2);
            }
            if (flag4) {
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d5, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d4, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d5, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d4, d8);
                }
            } else {
                if (y < l - 1 && this.blockAccess.isAirBlock(x, y + 1, z - 1)
                    && !GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d5, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d5, d7);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d4, d7);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d4, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d5, d6);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d5, d7);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d4, d7);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d4, d6);
                }
                if (y < l - 1 && this.blockAccess.isAirBlock(x, y + 1, z + 1)
                    && !GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d4, d7);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d5, d7);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d7);
                    tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d4, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d5, d8);
                    tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d7);
                }
            }
            if (flag5) {
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d8);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d4, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d8);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d5, d8);
                    tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d4, d8);
                }
            } else {
                if (y > 1 && this.blockAccess.isAirBlock(x, y - 1, z - 1)
                    && !GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d13, d5, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, d13, d4, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d4, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d13, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d5, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d4, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, d13, d4, d6);
                }
                if (y > 1 && this.blockAccess.isAirBlock(x, y - 1, z + 1)) {
                    if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                        tessellator.addVertexWithUV(d15, y - 0.005, d13, d4, d7);
                        tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d8);
                        tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d8);
                        tessellator.addVertexWithUV(d16, y - 0.005, d13, d5, d7);
                        tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d7);
                        tessellator.addVertexWithUV(d15, y - 0.005, d13, d4, d8);
                        tessellator.addVertexWithUV(d16, y - 0.005, d13, d5, d8);
                        tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d7);
                    }
                }
            }
        } else if (flag) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d10, y + 1, z, d21, d2);
                tessellator.addVertexWithUV(d10, y, z, d21, d3);
                tessellator.addVertexWithUV(d10, y, d13, d0, d3);
                tessellator.addVertexWithUV(d10, y + 1, d13, d0, d2);
                tessellator.addVertexWithUV(d10, y + 1, d13, d21, d2);
                tessellator.addVertexWithUV(d10, y, d13, d21, d3);
                tessellator.addVertexWithUV(d10, y, z, d0, d3);
                tessellator.addVertexWithUV(d10, y + 1, z, d0, d2);
            }
            if (!flag3 && !flag2) {
                tessellator.addVertexWithUV(d15, y + 1, d13, d4, d6);
                tessellator.addVertexWithUV(d15, y, d13, d4, d8);
                tessellator.addVertexWithUV(d16, y, d13, d5, d8);
                tessellator.addVertexWithUV(d16, y + 1, d13, d5, d6);
                tessellator.addVertexWithUV(d16, y + 1, d13, d4, d6);
                tessellator.addVertexWithUV(d16, y, d13, d4, d8);
                tessellator.addVertexWithUV(d15, y, d13, d5, d8);
                tessellator.addVertexWithUV(d15, y + 1, d13, d5, d6);
            }
            if ((flag4 || (y < l - 1 && this.blockAccess.isAirBlock(x, y + 1, z - 1)))
                && !GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d5, d6);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d5, d7);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d4, d7);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d4, d6);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d5, d6);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, z, d5, d7);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, z, d4, d7);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d4, d6);
            }
            if (flag5 || (y > 1 && this.blockAccess.isAirBlock(x, y - 1, z - 1))) {
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d13, d5, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, d13, d4, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d4, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, d13, d5, d6);
                    tessellator.addVertexWithUV(d15, y - 0.005, z, d5, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, z, d4, d7);
                    tessellator.addVertexWithUV(d16, y - 0.005, d13, d4, d6);
                }
            }
        } else if (flag1) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d10, y + 1, d13, d0, d2);
                tessellator.addVertexWithUV(d10, y, d13, d0, d3);
                tessellator.addVertexWithUV(d10, y, d14, d1, d3);
                tessellator.addVertexWithUV(d10, y + 1, d14, d1, d2);
                tessellator.addVertexWithUV(d10, y + 1, d14, d0, d2);
                tessellator.addVertexWithUV(d10, y, d14, d0, d3);
                tessellator.addVertexWithUV(d10, y, d13, d1, d3);
                tessellator.addVertexWithUV(d10, y + 1, d13, d1, d2);
            }
            if (!flag3 && !flag2) {
                tessellator.addVertexWithUV(d16, y + 1, d13, d4, d6);
                tessellator.addVertexWithUV(d16, y, d13, d4, d8);
                tessellator.addVertexWithUV(d15, y, d13, d5, d8);
                tessellator.addVertexWithUV(d15, y + 1, d13, d5, d6);
                tessellator.addVertexWithUV(d15, y + 1, d13, d4, d6);
                tessellator.addVertexWithUV(d15, y, d13, d4, d8);
                tessellator.addVertexWithUV(d16, y, d13, d5, d8);
                tessellator.addVertexWithUV(d16, y + 1, d13, d5, d6);
            }
            if ((flag4 || (y < l - 1 && this.blockAccess.isAirBlock(x, y + 1, z + 1)))
                && !GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d4, d7);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d8);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d8);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d5, d7);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d14, d4, d7);
                tessellator.addVertexWithUV(d15, y + 1 + 0.005, d13, d4, d8);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d13, d5, d8);
                tessellator.addVertexWithUV(d16, y + 1 + 0.005, d14, d5, d7);
            }
            if ((flag5 || (y > 1 && this.blockAccess.isAirBlock(x, y - 1, z + 1)))
                && !GlassPaneRenderer.skipBottomEdgeRendering) {
                tessellator.addVertexWithUV(d15, y - 0.005, d13, d4, d7);
                tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d8);
                tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d8);
                tessellator.addVertexWithUV(d16, y - 0.005, d13, d5, d7);
                tessellator.addVertexWithUV(d15, y - 0.005, d14, d4, d7);
                tessellator.addVertexWithUV(d15, y - 0.005, d13, d4, d8);
                tessellator.addVertexWithUV(d16, y - 0.005, d13, d5, d8);
                tessellator.addVertexWithUV(d16, y - 0.005, d14, d5, d7);
            }
        }
        cir.setReturnValue(true);
    }

    @Redirect(method = "renderBlockStainedGlassPane", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon tweakStainedPaneIcons(RenderBlocks instance, Block block, int side, int meta) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    /**
     * Originally an overwrite with the reason of "significant deviations from vanilla"
     * Refactored by Roadhog360 to use injects instead for compatibility reasons
     *
     * @author mist475
     * @author roadhog360
     */
    @SuppressWarnings("UnreachableCode")
    @Inject(method = "renderBlockStainedGlassPane", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockPane;canPaneConnectTo(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;)Z", ordinal = 3, shift = At.Shift.BY, by = 2, remap = false), cancellable = true)
    private void tweakStainedPaneRenderer(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir,
                                           @Local(name = "iicon") IIcon iicon,
                                           @Local(name = "d22") double d22, @Local(name = "d0") double d0, @Local(name = "d1") double d1, @Local(name = "d2") double d2,
                                           @Local(name = "d3") double d3, @Local(name = "d4") double d4, @Local(name = "d5") double d5, @Local(name = "d6") double d6,
                                           @Local(name = "d7") double d7, @Local(name = "d8") double d8, @Local(name = "d9") double d9, @Local(name = "d10") double d10,
                                           @Local(name = "d11") double d11, @Local(name = "d12") double d12, @Local(name = "d13") double d13, @Local(name = "d14") double d14,
                                           @Local(name = "d15") double d15, @Local(name = "d16") double d16, @Local(name = "d17") double d17, @Local(name = "d18") double d18,
                                           @Local(name = "flag") boolean flag, @Local(name = "flag1") boolean flag1,
                                           @Local(name = "flag2") boolean flag2, @Local(name = "flag3") boolean flag3) {
        Tessellator tessellator = Tessellator.instance;
        GlassPaneRenderer.renderThick((RenderBlocks) (Object)this, block, iicon, x, y, z, flag, flag1, flag2, flag3);
        boolean flag4 = !flag && !flag1 && !flag2 && !flag3;
        if (flag2 || flag4) {
            if (flag2 && flag3) {
                if (!flag) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d12, y + 0.999, d17, d2, d3);
                        tessellator.addVertexWithUV(d12, y + 0.001, d17, d2, d4);
                        tessellator.addVertexWithUV(x, y + 0.001, d17, d22, d4);
                        tessellator.addVertexWithUV(x, y + 0.999, d17, d22, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(x, y + 0.001, d17, d22, d4);
                    tessellator.addVertexWithUV(x, y + 0.999, d17, d22, d3);
                    tessellator.addVertexWithUV(d12, y + 0.999, d17, d2, d3);
                    tessellator.addVertexWithUV(d12, y + 0.001, d17, d2, d4);
                    tessellator.addVertexWithUV(d16, y + 0.001, d17, d1, d4);
                    tessellator.addVertexWithUV(d16, y + 0.999, d17, d1, d3);
                }
                if (!flag1) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(x, y + 0.999, d18, d22, d3);
                        tessellator.addVertexWithUV(x, y + 0.001, d18, d22, d4);
                        tessellator.addVertexWithUV(d12, y + 0.001, d18, d2, d4);
                        tessellator.addVertexWithUV(d12, y + 0.999, d18, d2, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(x, y + 0.999, d18, d22, d3);
                    tessellator.addVertexWithUV(x, y + 0.001, d18, d22, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d18, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d18, d0, d3);
                    tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
                    tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                    tessellator.addVertexWithUV(d12, y + 0.001, d18, d2, d4);
                    tessellator.addVertexWithUV(d12, y + 0.999, d18, d2, d3);
                }
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(x, y + 0.999, d18, d6, d7);
                    tessellator.addVertexWithUV(d12, y + 0.999, d18, d6, d8);
                    tessellator.addVertexWithUV(d12, y + 0.999, d17, d5, d8);
                    tessellator.addVertexWithUV(x, y + 0.999, d17, d5, d7);
                }
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d12, y + 0.001, d18, d5, d8);
                    tessellator.addVertexWithUV(x, y + 0.001, d18, d5, d7);
                    tessellator.addVertexWithUV(x, y + 0.001, d17, d6, d7);
                    tessellator.addVertexWithUV(d12, y + 0.001, d17, d6, d8);
                }
            } else {
                if (!flag && !flag4) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d16, y + 0.999, d17, d1, d3);
                        tessellator.addVertexWithUV(d16, y + 0.001, d17, d1, d4);
                        tessellator.addVertexWithUV(x, y + 0.001, d17, d22, d4);
                        tessellator.addVertexWithUV(x, y + 0.999, d17, d22, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(x, y + 0.001, d17, d22, d4);
                    tessellator.addVertexWithUV(x, y + 0.999, d17, d22, d3);
                }
                if (!flag1 && !flag4) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(x, y + 0.999, d18, d22, d3);
                        tessellator.addVertexWithUV(x, y + 0.001, d18, d22, d4);
                        tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                        tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(x, y + 0.999, d18, d22, d3);
                    tessellator.addVertexWithUV(x, y + 0.001, d18, d22, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d18, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d18, d0, d3);
                }
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(x, y + 0.999, d18, d6, d7);
                    tessellator.addVertexWithUV(d15, y + 0.999, d18, d6, d9);
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d5, d9);
                    tessellator.addVertexWithUV(x, y + 0.999, d17, d5, d7);
                }
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.001, d18, d5, d9);
                    tessellator.addVertexWithUV(x, y + 0.001, d18, d5, d7);
                    tessellator.addVertexWithUV(x, y + 0.001, d17, d6, d7);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d6, d9);
                }
            }
        } else if (!flag && !flag1 && !GlassPaneRenderer.skipPaneRendering) {
            tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
            tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
            tessellator.addVertexWithUV(d15, y + 0.001, d18, d1, d4);
            tessellator.addVertexWithUV(d15, y + 0.999, d18, d1, d3);
        }
        if ((flag3 || flag4) && !flag2) {
            if (!flag1 && !flag4) {
                if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, d18, d0, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, d18, d0, d4);
                    tessellator.addVertexWithUV(d12, y + 0.001, d18, d2, d4);
                    tessellator.addVertexWithUV(d12, y + 0.999, d18, d2, d3);
                }
            } else if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
                tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                tessellator.addVertexWithUV(d12, y + 0.001, d18, d2, d4);
                tessellator.addVertexWithUV(d12, y + 0.999, d18, d2, d3);
            }
            if (!flag && !flag4) {
                if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d12, y + 0.999, d17, d2, d3);
                    tessellator.addVertexWithUV(d12, y + 0.001, d17, d2, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                }
            } else if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d12, y + 0.999, d17, d2, d3);
                tessellator.addVertexWithUV(d12, y + 0.001, d17, d2, d4);
                tessellator.addVertexWithUV(d16, y + 0.001, d17, d1, d4);
                tessellator.addVertexWithUV(d16, y + 0.999, d17, d1, d3);
            }
            if (!GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(d16, y + 0.999, d18, d6, d10);
                tessellator.addVertexWithUV(d12, y + 0.999, d18, d6, d7);
                tessellator.addVertexWithUV(d12, y + 0.999, d17, d5, d7);
                tessellator.addVertexWithUV(d16, y + 0.999, d17, d5, d10);
            }
            if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                tessellator.addVertexWithUV(d12, y + 0.001, d18, d5, d8);
                tessellator.addVertexWithUV(d16, y + 0.001, d18, d5, d10);
                tessellator.addVertexWithUV(d16, y + 0.001, d17, d6, d10);
                tessellator.addVertexWithUV(d12, y + 0.001, d17, d6, d8);
            }
        } else if (!flag3 && !flag && !flag1 && !GlassPaneRenderer.skipPaneRendering) {
            tessellator.addVertexWithUV(d16, y + 0.999, d18, d0, d3);
            tessellator.addVertexWithUV(d16, y + 0.001, d18, d0, d4);
            tessellator.addVertexWithUV(d16, y + 0.001, d17, d1, d4);
            tessellator.addVertexWithUV(d16, y + 0.999, d17, d1, d3);
        }
        if (flag || flag4) {
            if (flag && flag1) {
                if (!flag2) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d15, y + 0.999, z, d22, d3);
                        tessellator.addVertexWithUV(d15, y + 0.001, z, d22, d4);
                        tessellator.addVertexWithUV(d15, y + 0.001, d14, d2, d4);
                        tessellator.addVertexWithUV(d15, y + 0.999, d14, d2, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, z, d22, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, z, d22, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d15, y + 0.999, d18, d1, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, d18, d1, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d14, d2, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d14, d2, d3);
                }
                if (!flag3) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d16, y + 0.999, d14, d2, d3);
                        tessellator.addVertexWithUV(d16, y + 0.001, d14, d2, d4);
                        tessellator.addVertexWithUV(d16, y + 0.001, z, d22, d4);
                        tessellator.addVertexWithUV(d16, y + 0.999, z, d22, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d16, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d16, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d16, y + 0.001, z, d22, d4);
                    tessellator.addVertexWithUV(d16, y + 0.999, z, d22, d3);
                    tessellator.addVertexWithUV(d16, y + 0.999, d14, d2, d3);
                    tessellator.addVertexWithUV(d16, y + 0.001, d14, d2, d4);
                    tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                    tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
                }
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d16, y + 0.999, z, d6, d7);
                    tessellator.addVertexWithUV(d15, y + 0.999, z, d5, d7);
                    tessellator.addVertexWithUV(d15, y + 0.999, d14, d5, d8);
                    tessellator.addVertexWithUV(d16, y + 0.999, d14, d6, d8);
                }
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.001, z, d5, d7);
                    tessellator.addVertexWithUV(d16, y + 0.001, z, d6, d7);
                    tessellator.addVertexWithUV(d16, y + 0.001, d14, d6, d8);
                    tessellator.addVertexWithUV(d15, y + 0.001, d14, d5, d8);
                }
            } else {
                if (!flag2 && !flag4) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d15, y + 0.999, z, d22, d3);
                        tessellator.addVertexWithUV(d15, y + 0.001, z, d22, d4);
                        tessellator.addVertexWithUV(d15, y + 0.001, d18, d1, d4);
                        tessellator.addVertexWithUV(d15, y + 0.999, d18, d1, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, z, d22, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, z, d22, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                }
                if (!flag3 && !flag4) {
                    if (!GlassPaneRenderer.skipPaneRendering) {
                        tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
                        tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                        tessellator.addVertexWithUV(d16, y + 0.001, z, d22, d4);
                        tessellator.addVertexWithUV(d16, y + 0.999, z, d22, d3);
                    }
                } else if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d16, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d16, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d16, y + 0.001, z, d22, d4);
                    tessellator.addVertexWithUV(d16, y + 0.999, z, d22, d3);
                }
                if (!GlassPaneRenderer.skipTopEdgeRendering) {
                    tessellator.addVertexWithUV(d16, y + 0.999, z, d6, d7);
                    tessellator.addVertexWithUV(d15, y + 0.999, z, d5, d7);
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d5, d9);
                    tessellator.addVertexWithUV(d16, y + 0.999, d17, d6, d9);
                }
                if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.001, z, d5, d7);
                    tessellator.addVertexWithUV(d16, y + 0.001, z, d6, d7);
                    tessellator.addVertexWithUV(d16, y + 0.001, d17, d6, d9);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d5, d9);
                }
            }
        } else if (!flag3 && !flag2 && !GlassPaneRenderer.skipPaneRendering) {
            tessellator.addVertexWithUV(d16, y + 0.999, d17, d1, d3);
            tessellator.addVertexWithUV(d16, y + 0.001, d17, d1, d4);
            tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
            tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
        }
        if ((flag1 || flag4) && !flag) {
            if (!flag2 && !flag4) {
                if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d15, y + 0.999, d17, d0, d3);
                    tessellator.addVertexWithUV(d15, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d15, y + 0.001, d14, d2, d4);
                    tessellator.addVertexWithUV(d15, y + 0.999, d14, d2, d3);
                }
            } else if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d15, y + 0.999, d18, d1, d3);
                tessellator.addVertexWithUV(d15, y + 0.001, d18, d1, d4);
                tessellator.addVertexWithUV(d15, y + 0.001, d14, d2, d4);
                tessellator.addVertexWithUV(d15, y + 0.999, d14, d2, d3);
            }
            if (!flag3 && !flag4) {
                if (!GlassPaneRenderer.skipPaneRendering) {
                    tessellator.addVertexWithUV(d16, y + 0.999, d14, d2, d3);
                    tessellator.addVertexWithUV(d16, y + 0.001, d14, d2, d4);
                    tessellator.addVertexWithUV(d16, y + 0.001, d17, d0, d4);
                    tessellator.addVertexWithUV(d16, y + 0.999, d17, d0, d3);
                }
            } else if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d16, y + 0.999, d14, d2, d3);
                tessellator.addVertexWithUV(d16, y + 0.001, d14, d2, d4);
                tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
                tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
            }
            if (!GlassPaneRenderer.skipTopEdgeRendering) {
                tessellator.addVertexWithUV(d16, y + 0.999, d18, d6, d10);
                tessellator.addVertexWithUV(d15, y + 0.999, d18, d5, d10);
                tessellator.addVertexWithUV(d15, y + 0.999, d14, d5, d8);
                tessellator.addVertexWithUV(d16, y + 0.999, d14, d6, d8);
            }
            if (!GlassPaneRenderer.skipBottomEdgeRendering) {
                tessellator.addVertexWithUV(d15, y + 0.001, d18, d5, d10);
                tessellator.addVertexWithUV(d16, y + 0.001, d18, d6, d10);
                tessellator.addVertexWithUV(d16, y + 0.001, d14, d6, d8);
                tessellator.addVertexWithUV(d15, y + 0.001, d14, d5, d8);
            }
        } else if (!flag1 && !flag3 && !flag2 && !GlassPaneRenderer.skipPaneRendering) {
            tessellator.addVertexWithUV(d15, y + 0.999, d18, d0, d3);
            tessellator.addVertexWithUV(d15, y + 0.001, d18, d0, d4);
            tessellator.addVertexWithUV(d16, y + 0.001, d18, d1, d4);
            tessellator.addVertexWithUV(d16, y + 0.999, d18, d1, d3);
        }
        if (!GlassPaneRenderer.skipTopEdgeRendering) {
            tessellator.addVertexWithUV(d16, y + 0.999, d17, d6, d9);
            tessellator.addVertexWithUV(d15, y + 0.999, d17, d5, d9);
            tessellator.addVertexWithUV(d15, y + 0.999, d18, d5, d10);
            tessellator.addVertexWithUV(d16, y + 0.999, d18, d6, d10);
        }
        if (!GlassPaneRenderer.skipBottomEdgeRendering) {
            tessellator.addVertexWithUV(d15, y + 0.001, d17, d5, d9);
            tessellator.addVertexWithUV(d16, y + 0.001, d17, d6, d9);
            tessellator.addVertexWithUV(d16, y + 0.001, d18, d6, d10);
            tessellator.addVertexWithUV(d15, y + 0.001, d18, d5, d10);
        }
        if (flag4) {
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(x, y + 0.999, d17, d0, d3);
                tessellator.addVertexWithUV(x, y + 0.001, d17, d0, d4);
                tessellator.addVertexWithUV(x, y + 0.001, d18, d1, d4);
                tessellator.addVertexWithUV(x, y + 0.999, d18, d1, d3);
                tessellator.addVertexWithUV(d12, y + 0.999, d18, d0, d3);
                tessellator.addVertexWithUV(d12, y + 0.001, d18, d0, d4);
                tessellator.addVertexWithUV(d12, y + 0.001, d17, d1, d4);
                tessellator.addVertexWithUV(d12, y + 0.999, d17, d1, d3);
            }
            if (!GlassPaneRenderer.skipPaneRendering) {
                tessellator.addVertexWithUV(d16, y + 0.999, z, d1, d3);
                tessellator.addVertexWithUV(d16, y + 0.001, z, d1, d4);
                tessellator.addVertexWithUV(d15, y + 0.001, z, d0, d4);
                tessellator.addVertexWithUV(d15, y + 0.999, z, d0, d3);
                tessellator.addVertexWithUV(d15, y + 0.999, d14, d0, d3);
                tessellator.addVertexWithUV(d15, y + 0.001, d14, d0, d4);
                tessellator.addVertexWithUV(d16, y + 0.001, d14, d1, d4);
                tessellator.addVertexWithUV(d16, y + 0.999, d14, d1, d3);
            }
        }
        cir.setReturnValue(true);
    }

    @Redirect(
        method = "renderCrossedSquares(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon redirectGetBlockIconFromSideAndMetadata(RenderBlocks instance, Block block, int side, int meta,
        Block specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "renderBlockDoublePlant(Lnet/minecraft/block/BlockDoublePlant;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockDoublePlant;func_149888_a(ZI)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockDoublePlant(BlockDoublePlant block, boolean top, int meta,
        BlockDoublePlant specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(
            block.func_149888_a(top, meta),
            block,
            blockAccess,
            x,
            y,
            z,
            -1);
    }

    @Inject(
        method = "renderStandardBlockWithColorMultiplier",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V", ordinal = 0),
        }
    )
    private void fixBetterGrassColorMultiplierZ(Block block, int x, int y, int z, float red, float green, float blue,
                                                CallbackInfoReturnable<Boolean> cir,
                                                @Local(name = "tessellator") Tessellator tessellator,
                                                @Local(name = "iicon") IIcon iicon,
                                                @Local(name = "f11") float r,
                                                @Local(name = "f14") float g,
                                                @Local(name = "f17") float b) {
        if (iicon.getIconName().equals("grass_top")) {
            tessellator.setColorOpaque_F(r * red, g * green, b * blue);
        }
    }

    @Inject(
        method = "renderStandardBlockWithColorMultiplier",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V", ordinal = 0),
        }
    )
    private void fixBetterGrassColorMultiplierX(Block block, int x, int y, int z, float red, float green, float blue,
                                                CallbackInfoReturnable<Boolean> cir,
                                                @Local(name = "tessellator") Tessellator tessellator,
                                                @Local(name = "iicon") IIcon iicon,
                                                @Local(name = "f12") float r,
                                                @Local(name = "f15") float g,
                                                @Local(name = "f18") float b) {
        if (iicon.getIconName().equals("grass_top")) {
            tessellator.setColorOpaque_F(r * red, g * green, b * blue);
        }
    }

    @Redirect(
        method = { "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockGrass;getIconSideOverlay()Lnet/minecraft/util/IIcon;",
            ordinal = 0))
    private IIcon redirectGrassSideOverLay1(Block block, int x, int y, int z, float red, float green, float blue) {
        return CTMUtils.getBlockIcon(
            BlockGrass.getIconSideOverlay(),
            block,
            blockAccess,
            x,
            y,
            z,
            2);
    }

    @Redirect(
        method = { "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockGrass;getIconSideOverlay()Lnet/minecraft/util/IIcon;",
            ordinal = 1))
    private IIcon redirectGrassSideOverLay2(Block block, int x, int y, int z, float red, float green, float blue) {
        return CTMUtils.getBlockIcon(
            BlockGrass.getIconSideOverlay(),
            block,
            blockAccess,
            x,
            y,
            z,
            3);
    }

    @Redirect(
        method = { "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockGrass;getIconSideOverlay()Lnet/minecraft/util/IIcon;",
            ordinal = 2))
    private IIcon redirectGrassSideOverLay3(Block block, int x, int y, int z, float red, float green, float blue) {
        return CTMUtils.getBlockIcon(
            BlockGrass.getIconSideOverlay(),
            block,
            blockAccess,
            x,
            y,
            z,
            4);
    }

    @Redirect(
        method = { "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockGrass;getIconSideOverlay()Lnet/minecraft/util/IIcon;",
            ordinal = 3))
    private IIcon redirectGrassSideOverLay4(Block block, int x, int y, int z, float red, float green, float blue) {
        return CTMUtils.getBlockIcon(
            BlockGrass.getIconSideOverlay(),
            block,
            blockAccess,
            x,
            y,
            z,
            5);
    }

    @Redirect(
        method = "renderBlockHopperMetadata(Lnet/minecraft/block/BlockHopper;IIIIZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderBlockHopperMetadata(RenderBlocks instance, Block block, int side, int meta,
        BlockHopper specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "getBlockIcon(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getIconSafe(Lnet/minecraft/util/IIcon;)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyGetBlockIcon(RenderBlocks instance, IIcon texture, Block block, IBlockAccess blockAccess, int x,
        int y, int z, int side) {
        return CTMUtils.getBlockIcon(
            this.getIconSafe(block.getIcon(blockAccess, x, y, z, side)),
            block,
            blockAccess,
            x,
            y,
            z,
            side);
    }

    @Redirect(
        method = "getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getIconSafe(Lnet/minecraft/util/IIcon;)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyGetBlockIconFromSideAndMetadata(RenderBlocks instance, IIcon texture, Block block, int side,
        int meta) {
        return CTMUtils
            .getBlockIcon(this.getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "getBlockIconFromSide(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getIconSafe(Lnet/minecraft/util/IIcon;)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyGetBlockIconFromSide(RenderBlocks instance, IIcon texture, Block block, int side) {
        return CTMUtils.getBlockIcon(
            getIconSafe(getIconSafe(block.getBlockTextureFromSide(side))),
            block,
            side);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSideAndMetadata(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
            ordinal = 1))
    private IIcon mcpatcherforge$redirectToGetBlockIcon(RenderBlocks instance, Block block, int side, int meta,
        Block specializedBlock, int x, int y, int z) {
        return CTMUtils.getBlockIcon(getIconSafe(block.getIcon(side, meta)), block, side, meta);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;getBlockIconFromSide(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;"))
    private IIcon mcpatcherforge$redirectToGetBlockIcon(RenderBlocks instance, Block block, int side,
        Block specializedBlock, int x, int y, int z) {
        return getBlockIcon(block, blockAccess, x, y, z, side);
    }

}
