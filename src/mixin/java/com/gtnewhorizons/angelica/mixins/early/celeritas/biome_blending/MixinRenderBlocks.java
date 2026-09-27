package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.prupe.mcpatcher.cc.ColorizeBlock;
import com.prupe.mcpatcher.ctm.CTMUtils;
import jss.notfine.config.MCPatcherForgeConfig;
import me.jellysquid.mods.sodium.client.gui.options.named.BiomeBlendMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

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

    @WrapMethod(method = {"renderFaceYNeg", "renderFaceYPos", "renderFaceZNeg", "renderFaceZPos", "renderFaceXNeg", "renderFaceXPos"})
    private void angelica$blendFace(Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (!angelica$vertexBlender.isActive()) {
            original.call(block, x, y, z, icon);
            return;
        }
        final BiomeBlendTessellator tessellator = (BiomeBlendTessellator) TessellatorManager.get();
        final BiomeVertexBlender blender = tessellator.angelica$getBiomeBlender();
        final IIcon originalIcon = AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled ? CTMUtils.getOriginalIcon(icon) : icon;
        if (blender != null && blender.isGrass() && icon != Blocks.grass.getIcon(1, 0) && icon != BlockGrass.getIconSideOverlay()
            && originalIcon != Blocks.grass.getIcon(1, 0) && originalIcon != BlockGrass.getIconSideOverlay()) {
            tessellator.angelica$setBiomeBlender(null);
        }
        try {
            original.call(block, x, y, z, icon);
        } finally {
            tessellator.angelica$setBiomeBlender(blender);
        }
    }
}
