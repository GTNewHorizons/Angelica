package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.prupe.mcpatcher.cc.ColorizeBlock;
import jss.notfine.config.MCPatcherForgeConfig;
import me.jellysquid.mods.sodium.client.gui.options.named.BiomeBlendMode;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
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

    @Surround(method = {"renderStandardBlock", "renderBlockLiquid"}, id = "blendBlock")
    private void angelica$blendBlock(Block block, int x, int y, int z) {
        final SmoothBiomeColorCache cache = SmoothBiomeColorCache.getActiveCache();
        @Surround.Carry("tessellator")
        BiomeBlendTessellator tessellator = null;
        @Surround.Carry("previous")
        BiomeVertexBlender previous = null;
        @Surround.Carry
        boolean blend = false;
        if (cache != null && ClientProxy.options().quality.biomeBlendMode == BiomeBlendMode.FANCY && SmoothBiomeColorCache.configuredRadius() != 0) {
            tessellator = (BiomeBlendTessellator) TessellatorManager.get();
            previous = tessellator.angelica$getBiomeBlender();
            final SmoothBiomeColorCache.ColorType type = angelica$colorType(block);
            blend = previous == null && type != null && !hasOverrideBlockTexture() && !EntityRenderer.anaglyphEnable
                && !(AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomColors.enabled && ColorizeBlock.hasCustomColors(block, blockAccess, x, y, z));
            if (blend) angelica$vertexBlender.setup(cache, type, x, y, z);
            tessellator.angelica$setBiomeBlender(blend ? angelica$vertexBlender : null);
        }
    }

    @Surround.Finally("blendBlock")
    private void angelica$endBlendBlock(@Surround.Carry("tessellator") BiomeBlendTessellator tessellator, @Surround.Carry("previous") BiomeVertexBlender previous,
                                        @Surround.Carry boolean blend) {
        if (tessellator == null) return;
        tessellator.angelica$setBiomeBlender(previous);
        if (blend) angelica$vertexBlender.clear();
    }

    @Unique
    private static SmoothBiomeColorCache.ColorType angelica$colorType(Block block) {
        if (block == Blocks.grass) return SmoothBiomeColorCache.ColorType.GRASS;
        if (block == Blocks.leaves || block == Blocks.leaves2) return SmoothBiomeColorCache.ColorType.FOLIAGE;
        if (block == Blocks.water || block == Blocks.flowing_water) return SmoothBiomeColorCache.ColorType.WATER;
        return null;
    }

    @Surround(method = {"renderStandardBlock", "renderBlockLiquid"}, id = "deferTint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;colorMultiplier(Lnet/minecraft/world/IBlockAccess;III)I"))
    private void angelica$deferTintEnter() {
        @Surround.Skip
        final boolean defer = ((BiomeBlendTessellator) TessellatorManager.get()).angelica$getBiomeBlender() == angelica$vertexBlender;
    }

    @Surround.Skipped("deferTint")
    private int angelica$deferTint() {
        return angelica$vertexBlender.deferBlockTint();
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceYNeg",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendYNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 0, x, y, z, icon);
    }

    @Surround.Finally("faceYNeg")
    private void angelica$endYNeg(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceYPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendYPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 1, x, y, z, icon);
    }

    @Surround.Finally("faceYPos")
    private void angelica$endYPos(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceZNeg",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendZNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 2, x, y, z, icon);
    }

    @Surround.Finally("faceZNeg")
    private void angelica$endZNeg(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceZPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendZPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 3, x, y, z, icon);
    }

    @Surround.Finally("faceZPos")
    private void angelica$endZPos(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceXNeg",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendXNeg(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 4, x, y, z, icon);
    }

    @Surround.Finally("faceXNeg")
    private void angelica$endXNeg(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }

    @Surround(method = {"renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial", "renderStandardBlockWithColorMultiplier"}, id = "faceXPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void angelica$blendXPos(RenderBlocks renderer, Block block, double x, double y, double z, IIcon icon) {
        @Surround.Carry
        final boolean tinted = angelica$vertexBlender.beginFace(renderer, 5, x, y, z, icon);
    }

    @Surround.Finally("faceXPos")
    private void angelica$endXPos(@Surround.Carry boolean tinted) {
        if (tinted) angelica$vertexBlender.endFace((RenderBlocks) (Object) this);
    }
}
