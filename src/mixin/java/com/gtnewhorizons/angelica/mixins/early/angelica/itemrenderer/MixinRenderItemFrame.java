package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.RenderItemFrame;
import net.minecraft.entity.item.EntityItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderItemFrame.class)
public class MixinRenderItemFrame {

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/RenderItemFrame;renderFrameItemAsBlock(Lnet/minecraft/entity/item/EntityItemFrame;)V"
        )
    )
    private void angelica$cacheFrame(RenderItemFrame renderer, EntityItemFrame frame, Operation<Void> original) {
        angelica$renderCached(renderer, frame, original, frame.hangingDirection);
    }

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/RenderItemFrame;func_147915_b(Lnet/minecraft/entity/item/EntityItemFrame;)V"
        )
    )
    private void angelica$cacheMapFrame(RenderItemFrame renderer, EntityItemFrame frame, Operation<Void> original) {
        angelica$renderCached(renderer, frame, original, 4 + frame.hangingDirection);
    }

    @Unique
    private static void angelica$renderCached(RenderItemFrame renderer, EntityItemFrame frame,
                                              Operation<Void> original, int keyIndex) {
        if (GLStateManager.isRecordingDisplayList() || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.shouldInterceptDraw(Tessellator.instance)) {
            original.call(renderer, frame);
            return;
        }
        int list = BlockRenderListManager.getItemFrameDisplayList(keyIndex);
        if (list == 0) {
            list = BlockRenderListManager.startCompiling();
            original.call(renderer, frame);
            BlockRenderListManager.endItemFrameCompiling(list, keyIndex);
        }
        GLStateManager.glCallList(list);
    }
}
