package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.client.registry.RenderingRegistry;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Unique
    private boolean isRenderingByType = false;

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
        try {
            return RenderingRegistry.instance().renderWorldBlock(rb, world, x, y, z, block, modelId);
        } catch (NullPointerException ignored) {
            rb.renderStandardBlock(AngelicaMod.blockError, x, y, z);
        }
        return false;
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
        try {
            return RenderingRegistry.instance().renderWorldBlock(rb, world, x, y, z, block, modelId);
        } catch (NullPointerException ignored) {
            rb.renderStandardBlock(AngelicaMod.blockError, x, y, z);
        }
        return false;
    }

    @Redirect(method = "renderStandardBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"))
    private boolean checkAOEnabled() {
        if ((this.isRenderingByType && Minecraft.isAmbientOcclusionEnabled() && SodiumClientMod.options().quality.useSodiumAO) ||
            (AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo())) {
            return false; // Force sodium pipeline with Iris or for standard blocks rendered from renderBlockByRenderType when using AO
        }

        return Minecraft.isAmbientOcclusionEnabled();
    }
}
