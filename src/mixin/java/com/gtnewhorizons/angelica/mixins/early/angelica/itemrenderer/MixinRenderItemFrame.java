package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.RenderItemFrame;
import net.minecraft.entity.item.EntityItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderItemFrame.class)
public class MixinRenderItemFrame {

    @Surround(
        method = "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/RenderItemFrame;renderFrameItemAsBlock(Lnet/minecraft/entity/item/EntityItemFrame;)V"
        ),
        id = "frame"
    )
    private void angelica$cacheFrame(RenderItemFrame renderer, EntityItemFrame frame) {
        @Surround.Carry("key")
        final int key = frame.hangingDirection;
        @Surround.Carry("list")
        final int list = angelica$beginCached(key);
        @Surround.Skip
        final boolean cached = list < 0;
    }

    @Surround.Return("frame")
    private void angelica$endFrame(@Surround.Carry("key") int key, @Surround.Carry("list") int list) {
        BlockRenderListManager.endItemFrameCompiling(list, key);
    }

    @Surround.Catch("frame")
    private void angelica$abortFrame(Throwable error, @Surround.Carry("list") int list) {
        BlockRenderListManager.abortCompiling(list);
    }

    @Surround(
        method = "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/RenderItemFrame;func_147915_b(Lnet/minecraft/entity/item/EntityItemFrame;)V"
        ),
        id = "map"
    )
    private void angelica$cacheMapFrame(RenderItemFrame renderer, EntityItemFrame frame) {
        @Surround.Carry("key")
        final int key = 4 + frame.hangingDirection;
        @Surround.Carry("list")
        final int list = angelica$beginCached(key);
        @Surround.Skip
        final boolean cached = list < 0;
    }

    @Surround.Return("map")
    private void angelica$endMapFrame(@Surround.Carry("key") int key, @Surround.Carry("list") int list) {
        BlockRenderListManager.endItemFrameCompiling(list, key);
    }

    @Surround.Catch("map")
    private void angelica$abortMapFrame(Throwable error, @Surround.Carry("list") int list) {
        BlockRenderListManager.abortCompiling(list);
    }

    @Unique
    private static int angelica$beginCached(int keyIndex) {
        if (GLStateManager.isRecordingDisplayList() || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.shouldInterceptDraw(Tessellator.instance)) {
            return 0;
        }
        return BlockRenderListManager.callOrStartCompiling(BlockRenderListManager.getItemFrameDisplayList(keyIndex));
    }
}
