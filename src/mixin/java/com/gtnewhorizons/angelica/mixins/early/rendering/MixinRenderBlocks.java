package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.api.ExtCeleritasRenderBlocks;
import com.gtnewhorizons.angelica.common.BlockError;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.IsbrhDispatch;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.ctm.CompactCtmQuadProcessor;
import com.prupe.mcpatcher.ctm.RenderBlockState;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks implements ExtCeleritasRenderBlocks {
    @Shadow
    public abstract boolean renderStandardBlockWithColorMultiplier(Block p_147736_1_, int p_147736_2_, int p_147736_3_, int p_147736_4_, float p_147736_5_, float p_147736_6_, float p_147736_7_);

    @Unique
    private static final Set<String> isbrhExceptionCache = ConcurrentHashMap.newKeySet();

    @Unique
    private static final Object2IntOpenHashMap<Class<? extends Exception>> exceptionErrorBlockMap = new Object2IntOpenHashMap<>();

    static {
        exceptionErrorBlockMap.put(NullPointerException.class, 0);
        exceptionErrorBlockMap.put(ArrayIndexOutOfBoundsException.class, 1);
    }

    @Unique
    private boolean isRenderingByType = false;

    private boolean applyingCeleritasAO = false;

    @Surround(method = "renderBlockByRenderType", id = "byType")
    private void angelica$enterByType() {
        @Surround.Carry final boolean wasByType = this.isRenderingByType;
        CTMUtils.clearCurrentCompact();
        this.isRenderingByType = true;
    }

    @Surround.Finally("byType")
    private void angelica$exitByType(@Surround.Carry boolean wasByType) {
        CTMUtils.clearCurrentCompact();
        this.isRenderingByType = wasByType;
    }

    /**
     * Wraps ISBRH rendering in a try/catch to ignore NPE, as mods commonly like to not null-guard the tile entity casting, and Sodium introduces
     * a race condition where when a block is broken, the TE can be removed from the world before the render thread gets to it, but the block data
     * is deep copied to the thread, so it still tries to render the block.
     *
     * FMLRenderAccessLibrary is an old Forge remnant of Optifine compat. In a deobfuscated environment it lives in net.minecraft.src, in an
     * obfuscated prod environment it is moved into the root unnamed package. Both are targeted; only the one present matches.
     */
    @Surround(
        method = "renderBlockByRenderType",
        id = "isbrhCatch",
        at = {
            @At(
                value = "INVOKE",
                target = "LFMLRenderAccessLibrary;renderWorldBlock(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;I)Z",
                remap = false
            ),
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/src/FMLRenderAccessLibrary;renderWorldBlock(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;I)Z",
                remap = false
            )
        },
        require = 1
    )
    private void angelica$isbrhCatchEnter() {
    }

    @Surround.Catch(value = "isbrhCatch", handle = true)
    private boolean angelica$isbrhCatchHandler(Exception e, RenderBlocks rb, IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        CTMUtils.clearCurrentCompact();
        int meta = exceptionErrorBlockMap.getOrDefault(e.getClass(), 0);
        rb.overrideBlockTexture = BlockError.icons[meta];
        rb.renderStandardBlock(ClientProxy.blockError, x, y, z);
        rb.overrideBlockTexture = null;

        String key = block.getUnlocalizedName() + ":" + meta;
        if (isbrhExceptionCache.add(key)) {
            AngelicaClientTweaker.LOGGER.warn("Caught an exception during ISBRH rendering for {} at position {}, {}, {} with renderer ID {}", block.getUnlocalizedName(), x, y, z, modelId, e);
        }
        return false;
    }

    @Surround(method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
                         "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z" }, id = "celeritasAo", require = 2)
    private void angelica$celeritasAoEnter() {
        @Surround.Skip boolean skip = angelica$shouldApplyCeleritasAO();
    }

    @Surround.Skipped("celeritasAo")
    private boolean angelica$celeritasAoSkipped(Block block, int x, int y, int z, float r, float g, float b) {
        this.applyingCeleritasAO = true;
        try {
            return this.renderStandardBlockWithColorMultiplier(block, x, y, z, r, g, b);
        } finally {
            this.applyingCeleritasAO = false;
        }
    }

    @Redirect(method = "renderStandardBlockWithColorMultiplier",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getMixedBrightnessForBlock(Lnet/minecraft/world/IBlockAccess;III)I"),
        require = 1)
    private int angelica$skipDiscardedBrightness(Block block, IBlockAccess world, int x, int y, int z, @Local(ordinal = 0) Tessellator tessellator) {
        final boolean discard = this.applyingCeleritasAO && this.isRenderingByType
            && ((Object) this).getClass() == RenderBlocks.class
            && !IsbrhDispatch.isRenderingWorldBlock()
            && ((StateAwareTessellator) tessellator).angelica$isCeleritasMeshing();
        return discard ? 0 : block.getMixedBrightnessForBlock(world, x, y, z);
    }

    @Override
    public boolean angelica$shouldApplyCeleritasAO() {
        if (!((StateAwareTessellator) TessellatorManager.get()).angelica$isCeleritasMeshing()) return false;
        return (this.isRenderingByType && Minecraft.isAmbientOcclusionEnabled() && ClientProxy.options().quality.useCeleritasSmoothLighting) ||
            (Iris.enabled && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo());
    }

    @Override
    public void angelica$setApplyingCeleritasAO(boolean val) {
        this.applyingCeleritasAO = val;
    }

    /**
     * Widen the grass identity check ({@code block != Blocks.grass}) to cover any BlockGrass subclass
     * (e.g. BOP's loamy/sandy/silty grass). When the block being rendered IS a BlockGrass, we return
     * it in place of {@code Blocks.grass} so the reference comparison evaluates to {@code false},
     * giving it the same "no color multiplier on sides/bottom" treatment as vanilla grass.
     */
    @ModifyExpressionValue(method = "renderStandardBlockWithColorMultiplier",
        at = @At(value = "FIELD", target = "Lnet/minecraft/init/Blocks;grass:Lnet/minecraft/block/BlockGrass;", opcode = Opcodes.GETSTATIC))
    private BlockGrass angelica$widenGrassCheck(BlockGrass grassBlock, @Local(argsOnly = true, ordinal = 0) Block block) {
        return (block instanceof BlockGrass bg && block == ClientProxy.bopGrass) ? bg : grassBlock;
    }

    /* Disable diffuse when celeritas AO is in use */
    @ModifyExpressionValue(method = "renderStandardBlockWithColorMultiplier", at = @At(value = "CONSTANT", args = "floatValue=0.5", ordinal = 0))
    private float noBottomDiffuse(float original) {
        return this.applyingCeleritasAO ? 1.0f : original;
    }

    @ModifyExpressionValue(method = "renderStandardBlockWithColorMultiplier", at = @At(value = "CONSTANT", args = "floatValue=0.6", ordinal = 0))
    private float noXDiffuse(float original) {
        return this.applyingCeleritasAO ? 1.0f : original;
    }

    @ModifyExpressionValue(method = "renderStandardBlockWithColorMultiplier", at = @At(value = "CONSTANT", args = "floatValue=0.8", ordinal = 0))
    private float noZDiffuse(float original) {
        return this.applyingCeleritasAO ? 1.0f : original;
    }

    @ModifyExpressionValue(method = { "renderStandardBlockWithColorMultiplier" },
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/renderer/RenderBlocks;renderAllFaces:Z"))
    private boolean applyAOBrightness(boolean original, @Local(ordinal = 0) Tessellator tessellator) {
        ((StateAwareTessellator)tessellator).angelica$setAppliedAo(this.applyingCeleritasAO);
        return original;
    }

    @Inject(method = { "renderStandardBlockWithColorMultiplier" },
        at = @At("RETURN"))
    private void resetAOFlag(Block p_147736_1_, int p_147736_2_, int p_147736_3_, int p_147736_4_, float p_147736_5_, float p_147736_6_, float p_147736_7_, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) Tessellator tessellator) {
        ((StateAwareTessellator)tessellator).angelica$setAppliedAo(false);
    }

    @Shadow
    public IBlockAccess blockAccess;

    @Unique
    private boolean angelica$handleCompactCtmFace(IIcon icon, ForgeDirection direction) {
        CTMUtils.CTMCompactContext ctx = CTMUtils.getCurrentCompact();
        if (ctx == null) {
            return false;
        }
        CompactCtmQuadProcessor processor = ctx.compact().getProcessor();
        RenderBlockState renderBlockState = ctx.renderBlockState();
        CTMUtils.clearCurrentCompact();
        if (this.blockAccess == null || renderBlockState.getBlockAccess() == null) {
            return false;
        }

        RenderBlocks rb = (RenderBlocks) (Object) this;
        if (rb.hasOverrideBlockTexture()) {
            return false;
        }
        return processor.processFace(rb, renderBlockState, icon, direction.ordinal());
    }

    @Surround(method = "renderFaceYNeg", id = "ctmYNeg")
    private void angelica$ctmYNeg(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.DOWN);
    }

    @Surround(method = "renderFaceYPos", id = "ctmYPos")
    private void angelica$ctmYPos(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.UP);
    }

    @Surround(method = "renderFaceZNeg", id = "ctmZNeg")
    private void angelica$ctmZNeg(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.NORTH);
    }

    @Surround(method = "renderFaceZPos", id = "ctmZPos")
    private void angelica$ctmZPos(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.SOUTH);
    }

    @Surround(method = "renderFaceXNeg", id = "ctmXNeg")
    private void angelica$ctmXNeg(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.WEST);
    }

    @Surround(method = "renderFaceXPos", id = "ctmXPos")
    private void angelica$ctmXPos(Block block, double x, double y, double z, IIcon icon) {
        @Surround.Skip final boolean handled = angelica$handleCompactCtmFace(icon, ForgeDirection.EAST);
    }

    @Inject(method = "renderStandardBlock(Lnet/minecraft/block/Block;III)Z", at = @At("RETURN"))
    private void compactCtm_resetAfterBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        CTMUtils.clearCurrentCompact();
    }

    @Inject(method = "renderBlockAsItem", at = @At("HEAD"))
    private void compactCtm_resetBeforeItem(Block block, int meta, float brightness, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
    }
}
