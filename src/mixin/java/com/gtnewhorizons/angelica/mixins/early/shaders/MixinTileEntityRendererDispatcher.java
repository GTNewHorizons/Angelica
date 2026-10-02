package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.RenderRecovery;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBlendScope;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import com.gtnewhorizons.angelica.rendering.tesr.TileEntityLight;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Shadow
    public World field_147550_f;

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("HEAD"))
    private void iris$beginBlockEntity(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        RenderRecovery.throwIfCrashTestArmed();
        TesrBlendScope.enter();
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(te == null ? null : te.getBlockType(), TesrProviderDispatch.blockMetadata(te));
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;
        PassRebindGate.rebindIfDirty();
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$endBlockEntity(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
        TesrAttribution.currentRenderable = null;
        TesrBlendScope.exit();
    }

    @ModifyExpressionValue(method = "renderTileEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLightBrightnessForSkyBlocks(IIII)I"))
    private int iris$tileEntityLight(int vanillaLight, @Local(argsOnly = true) TileEntity te) {
        if (!IrisApi.getInstance().isShaderPackInUse()) return vanillaLight;
        return TileEntityLight.packedLight(field_147550_f, te.xCoord, te.yCoord, te.zCoord, vanillaLight);
    }
}
