package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.common.BlockError;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.ctm.CompactCtmQuadProcessor;
import com.prupe.mcpatcher.ctm.TileOverride;
import com.prupe.mcpatcher.ctm.TileOverrideImpl;
import cpw.mods.fml.client.registry.RenderingRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {
    @Shadow
    public abstract boolean renderStandardBlockWithColorMultiplier(Block p_147736_1_, int p_147736_2_, int p_147736_3_, int p_147736_4_, float p_147736_5_, float p_147736_6_, float p_147736_7_);

    @Unique
    private static final ObjectOpenHashSet<String> isbrhExceptionCache = new ObjectOpenHashSet<>();

    @Unique
    private static final Object2IntOpenHashMap<Class<? extends Exception>> exceptionErrorBlockMap = new Object2IntOpenHashMap<>();

    static {
        exceptionErrorBlockMap.put(NullPointerException.class, 0);
        exceptionErrorBlockMap.put(ArrayIndexOutOfBoundsException.class, 1);
    }

    @Unique
    private boolean isRenderingByType = false;

    private boolean applyingCeleritasAO = false;

    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"))
    private void renderingByTypeEnable(CallbackInfoReturnable<Boolean> ci) {
        this.isRenderingByType = true;
    }

    @Inject(method = "renderBlockByRenderType", at = @At("TAIL"))
    private void renderingByTypeDisable(CallbackInfoReturnable<Boolean> ci) {
        this.isRenderingByType = false;
    }

    /**
     * This mixin and the one below(wrapRenderWorldBlockDeobfuscated) achieve the same goal. The goal is to wrap ISBRH rendering in a try/catch
     * to ignore NPE, as mods commonly like to not null-guard the tile entity casting, and Sodium introduces a race condition where when a block
     * is broken, the TE can be removed from the world before the render thread gets to it, but the block data is deep copied to the thread, so
     * it still tries to render the block.

     * The reason there's two mixins to the same thing for this, is because FMLRenderAccessLibrary is an old Forge remnant of Optifine compat
     * whereby they provided this class for Optifine to be able to access Forge's rendering methods. For some reason, in a deobfuscated environment,
     * that class lives in net.minecraft.src.FMLRenderAccessLibrary. However in an obfuscated prod environment, it gets moved into the root unnamed
     * package. So basically, only one of these two redirects will actually end up getting applied, and the other will fail, based on what environment
     * you're running in.
     */
    @Redirect(
        method = "renderBlockByRenderType",
        at = @At(
            value = "INVOKE",
            target = "LFMLRenderAccessLibrary;renderWorldBlock(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;I)Z",
            remap = false
        ),
        expect = 0
    )
    private boolean wrapRenderWorldBlockObfuscated(RenderBlocks rb, IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        return handleISBRHException(rb, world, x, y, z, block, modelId);
    }

    @Redirect(
        method = "renderBlockByRenderType",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/src/FMLRenderAccessLibrary;renderWorldBlock(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;I)Z",
            remap = false
        ),
        expect = 0
    )
    private boolean wrapRenderWorldBlockDeobfuscated(RenderBlocks rb, IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        return handleISBRHException(rb, world, x, y, z, block, modelId);
    }

    /**
     * @author embeddedt
     * @reason When vanilla would render with AO, hijack the rendering logic and render using flat lighting instead.
     */
    @Inject(method = { "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial" }, at = @At("HEAD"), cancellable = true)
    private void handleCeleritasAo(Block block, int x, int y, int z, float r, float g, float b, CallbackInfoReturnable<Boolean> cir) {
        if ((this.isRenderingByType && Minecraft.isAmbientOcclusionEnabled() && ClientProxy.options().quality.useCeleritasSmoothLighting) ||
            (Iris.enabled && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo())) {
            this.applyingCeleritasAO = true;
            try {
                cir.setReturnValue(this.renderStandardBlockWithColorMultiplier(block, x, y, z, r, g, b));
            } finally {
                this.applyingCeleritasAO = false;
            }
        }
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

    @SuppressWarnings("deprecation")
    private boolean handleISBRHException(RenderBlocks rb, IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        try {
            return RenderingRegistry.instance().renderWorldBlock(rb, world, x, y, z, block, modelId);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
            // Render Error Block
            int meta = exceptionErrorBlockMap.getOrDefault(e.getClass(), 0);
            rb.overrideBlockTexture = BlockError.icons[exceptionErrorBlockMap.getOrDefault(e.getClass(), 0)];
            rb.renderStandardBlock(ClientProxy.blockError, x, y, z);
            rb.overrideBlockTexture = null;

            // Check if we've already caught the exception for this block and log it if we haven't
            String key = block.getUnlocalizedName() + ":" + meta;
            if (isbrhExceptionCache.add(key)) {
                AngelicaClientTweaker.LOGGER.warn("Caught an exception during ISBRH rendering for {} at position {}, {}, {} with renderer ID {}", block.getUnlocalizedName(), x, y, z, modelId, e);
            }
        }
        return false;
    }

    @ModifyExpressionValue(method = { "renderFaceXPos", "renderFaceYPos", "renderFaceZPos",
                              "renderFaceXNeg", "renderFaceYNeg", "renderFaceZNeg" },
                    at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/renderer/RenderBlocks;enableAO:Z"))
    private boolean applyAOBrightness(boolean original, @Local(ordinal = 0) Tessellator tessellator) {
        ((StateAwareTessellator)tessellator).angelica$setAppliedAo(this.applyingCeleritasAO);
        return original;
    }

    @Inject(method = { "renderFaceXPos", "renderFaceYPos", "renderFaceZPos",
        "renderFaceXNeg", "renderFaceYNeg", "renderFaceZNeg" },
        at = @At("RETURN"))
    private void resetAOFlag(Block p_147764_1_, double p_147764_2_, double p_147764_4_, double p_147764_6_, IIcon p_147764_8_, CallbackInfo ci,
                             @Local(ordinal = 0) Tessellator tessellator) {
        ((StateAwareTessellator)tessellator).angelica$setAppliedAo(false);
    }

    @Shadow
    public IBlockAccess blockAccess;

    @Unique
    private boolean compactCtmRendering = false;

    @Unique
    private CompactCtmQuadProcessor compactCtmProcessor;

    @Unique
    private CompactCtmQuadProcessor getCompactProcessor() {
        TileOverrideImpl.CTMCompact compact = CTMUtils.getCurrentCompact();
        if (compact == null) {
            return null;
        }
        if (this.compactCtmProcessor == null) {
            this.compactCtmProcessor = new CompactCtmQuadProcessor(
                compact.getIcons(),
                compact.getCtmProperties()
            );
        }
        return this.compactCtmProcessor;
    }

    private void handleCompactCtmFace(int face, Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        // recursion protection
        if (compactCtmRendering) {
            return;
        }

        if (this.blockAccess == null) {
            return;
        }

        CompactCtmQuadProcessor processor = getCompactProcessor();
        if (processor == null) {
            return;
        }

        compactCtmRendering = true;
        try {
            boolean handled = processor.processFace(
                (RenderBlocks)(Object)this,
                this.blockAccess,
                (int) x, (int) y, (int) z,
                face,
                icon
            );

            if (handled) {
                ci.cancel();
            }
        } finally {
            compactCtmRendering = false;
        }
    }

    @Inject(method = "renderFaceYNeg", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceYNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(0, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderFaceYPos", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceYPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(1, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderFaceZNeg", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceZNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(2, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderFaceZPos", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceZPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(3, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderFaceXNeg", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceXNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(4, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderFaceXPos", at = @At("HEAD"), cancellable = true)
    private void compactCtm_onRenderFaceXPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        handleCompactCtmFace(5, block, x, y, z, icon, ci);
    }

    @Inject(method = "renderStandardBlock(Lnet/minecraft/block/Block;III)Z", at = @At("RETURN"))
    private void compactCtm_resetAfterBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        this.compactCtmProcessor = null;
        CTMUtils.clearCurrentCompact();
    }
}
