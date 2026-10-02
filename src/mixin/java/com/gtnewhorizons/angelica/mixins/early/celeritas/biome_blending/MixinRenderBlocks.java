package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.prupe.mcpatcher.cc.ColorizeBlock;
import com.prupe.mcpatcher.ctm.CTMUtils;
import jss.notfine.config.MCPatcherForgeConfig;
import me.jellysquid.mods.sodium.client.gui.options.named.BiomeBlendMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {
    @Shadow public IBlockAccess blockAccess;
    @Shadow public abstract boolean hasOverrideBlockTexture();
    @Unique private final BiomeVertexBlender angelica$vertexBlender = new BiomeVertexBlender();

    @WrapMethod(method = {"renderStandardBlock", "renderBlockLiquid"})
    private boolean angelica$blendBlock(Block block, int x, int y, int z, Operation<Boolean> original) {
        final SmoothBiomeColorCache cache = SmoothBiomeColorCache.getActiveCache();
        if (cache == null || ClientProxy.options().quality.biomeBlendMode != BiomeBlendMode.FANCY || SmoothBiomeColorCache.configuredRadius() == 0) {
            return original.call(block, x, y, z);
        }
        final BiomeBlendTessellator tessellator = (BiomeBlendTessellator) TessellatorManager.get();
        final BiomeVertexBlender previous = tessellator.angelica$getBiomeBlender();
        final SmoothBiomeColorCache.ColorType type = angelica$colorType(block);
        final boolean blend = previous == null && type != null && !hasOverrideBlockTexture() && !EntityRenderer.anaglyphEnable
            && !(AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomColors.enabled && ColorizeBlock.hasCustomColors(block, blockAccess, x, y, z));
        if (blend) angelica$vertexBlender.setup(cache, type, x, y, z);
        tessellator.angelica$setBiomeBlender(blend ? angelica$vertexBlender : null);
        try {
            return original.call(block, x, y, z);
        } finally {
            tessellator.angelica$setBiomeBlender(previous);
            if (blend) angelica$vertexBlender.clear();
        }
    }

    @Unique
    private static SmoothBiomeColorCache.ColorType angelica$colorType(Block block) {
        if (block == Blocks.grass) return SmoothBiomeColorCache.ColorType.GRASS;
        if (block == Blocks.leaves || block == Blocks.leaves2) return SmoothBiomeColorCache.ColorType.FOLIAGE;
        if (block == Blocks.water || block == Blocks.flowing_water) return SmoothBiomeColorCache.ColorType.WATER;
        return null;
    }

    @WrapOperation(method = {"renderStandardBlock", "renderBlockLiquid"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;colorMultiplier(Lnet/minecraft/world/IBlockAccess;III)I"))
    private int angelica$deferLocalTint(Block block, IBlockAccess access, int x, int y, int z, Operation<Integer> original) {
        return ((BiomeBlendTessellator) TessellatorManager.get()).angelica$getBiomeBlender() == angelica$vertexBlender
            ? angelica$vertexBlender.deferBlockTint() : original.call(block, access, x, y, z);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendYNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 0, block, x, y, z, icon, original);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendYPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 1, block, x, y, z, icon, original);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendZNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 2, block, x, y, z, icon, original);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendZPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 3, block, x, y, z, icon, original);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendXNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 4, block, x, y, z, icon, original);
    }

    @WrapOperation(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendXPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        angelica$blendFace(renderer, 5, block, x, y, z, icon, original);
    }

    @Unique
    private void angelica$blendFace(RenderBlocks renderer, int face, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (!angelica$vertexBlender.isActive()) {
            original.call(renderer, block, x, y, z, icon);
            return;
        }
        final Tessellator tessellator = TessellatorManager.get();
        if (((BiomeBlendTessellator) tessellator).angelica$getBiomeBlender() != angelica$vertexBlender) {
            original.call(renderer, block, x, y, z, icon);
            return;
        }
        final IIcon originalIcon = AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled ? CTMUtils.getOriginalIcon(icon) : icon;
        if (angelica$vertexBlender.isGrass() && icon != Blocks.grass.getIcon(1, 0) && icon != BlockGrass.getIconSideOverlay()
            && originalIcon != Blocks.grass.getIcon(1, 0) && originalIcon != BlockGrass.getIconSideOverlay()) {
            original.call(renderer, block, x, y, z, icon);
            return;
        }

        final float rtl = renderer.colorRedTopLeft, gtl = renderer.colorGreenTopLeft, btl = renderer.colorBlueTopLeft;
        final float rbl = renderer.colorRedBottomLeft, gbl = renderer.colorGreenBottomLeft, bbl = renderer.colorBlueBottomLeft;
        final float rbr = renderer.colorRedBottomRight, gbr = renderer.colorGreenBottomRight, bbr = renderer.colorBlueBottomRight;
        final float rtr = renderer.colorRedTopRight, gtr = renderer.colorGreenTopRight, btr = renderer.colorBlueTopRight;
        final boolean ao = renderer.enableAO;
        final int ltl = renderer.brightnessTopLeft, lbl = renderer.brightnessBottomLeft;
        final int lbr = renderer.brightnessBottomRight, ltr = renderer.brightnessTopRight;
        final int color = tessellator.color, brightness = tessellator.brightness;
        if (!ao) {
            final int abgr = BiomeVertexBlender.nativeToABGR(color);
            renderer.colorRedTopLeft = renderer.colorRedBottomLeft = renderer.colorRedBottomRight = renderer.colorRedTopRight = (abgr & 255) / 255.0f;
            renderer.colorGreenTopLeft = renderer.colorGreenBottomLeft = renderer.colorGreenBottomRight = renderer.colorGreenTopRight = (abgr >> 8 & 255) / 255.0f;
            renderer.colorBlueTopLeft = renderer.colorBlueBottomLeft = renderer.colorBlueBottomRight = renderer.colorBlueTopRight = (abgr >> 16 & 255) / 255.0f;
            renderer.brightnessTopLeft = renderer.brightnessBottomLeft = renderer.brightnessBottomRight = renderer.brightnessTopRight = brightness;
            renderer.enableAO = true;
        }
        try {
            angelica$vertexBlender.tintFace(renderer, face, x, y, z);
            original.call(renderer, block, x, y, z, icon);
        } finally {
            renderer.colorRedTopLeft = rtl; renderer.colorGreenTopLeft = gtl; renderer.colorBlueTopLeft = btl;
            renderer.colorRedBottomLeft = rbl; renderer.colorGreenBottomLeft = gbl; renderer.colorBlueBottomLeft = bbl;
            renderer.colorRedBottomRight = rbr; renderer.colorGreenBottomRight = gbr; renderer.colorBlueBottomRight = bbr;
            renderer.colorRedTopRight = rtr; renderer.colorGreenTopRight = gtr; renderer.colorBlueTopRight = btr;
            renderer.enableAO = ao;
            if (!ao) {
                renderer.brightnessTopLeft = ltl; renderer.brightnessBottomLeft = lbl;
                renderer.brightnessBottomRight = lbr; renderer.brightnessTopRight = ltr;
                tessellator.color = color;
                tessellator.brightness = brightness;
            }
        }
    }
}
