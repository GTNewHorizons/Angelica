package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.GuiIngameForge;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {

    @Shadow(remap = false)
    private FontRenderer fontrenderer;

    @Unique private ByteBuffer angelica$data = memAlloc(2048);
    @Unique private int angelica$ptrOffset = 0;
    @Unique private long angelica$basePtr = memAddress0(angelica$data);

    @Redirect(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 0,
            remap = true))
    private int renderBackgroundLeft(FontRenderer fontRenderer, String text, int x, int y, int color) {
        angelica$writeQuad(
            x - 1, y - 1,
            x + fontRenderer.getStringWidth(text) + 1,
            y + fontRenderer.FONT_HEIGHT
        );
        return fontRenderer.drawString(text, x, y, 0xe0e0e0);
    }

    @Redirect(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 1,
            remap = true))
    private int renderBackgroundRight(FontRenderer fontRenderer, String text, int x, int y, int color) {
        x += 8;
        angelica$writeQuad(
            x - 1, y - 1,
            x + fontRenderer.getStringWidth(text) + 1, y + fontRenderer.FONT_HEIGHT
        );
        return fontRenderer.drawString(text, x, y, 0xe0e0e0);
    }

    @Unique
    private void angelica$writeQuad(int left, int bottom, int right, int top) {
        if ((angelica$ptrOffset + 48) > angelica$data.capacity()) {
            angelica$data = memRealloc(angelica$data, angelica$data.capacity() * 2);
            angelica$basePtr = memAddress0(angelica$data);
        }
        final long ptr = angelica$basePtr + angelica$ptrOffset;
        memPutFloat(ptr, left);
        memPutFloat(ptr + 4, top);
        memPutFloat(ptr + 8, 0);

        memPutFloat(ptr + 12, right);
        memPutFloat(ptr + 16, top);
        memPutFloat(ptr + 20, 0);

        memPutFloat(ptr + 24, right);
        memPutFloat(ptr + 28, bottom);
        memPutFloat(ptr + 32, 0);

        memPutFloat(ptr + 36, left);
        memPutFloat(ptr + 40, bottom);
        memPutFloat(ptr + 44, 0);

        angelica$ptrOffset += 48;
    }

    @Inject(
        method = "renderHUDText",
        at = @At("HEAD"),
        remap = false)
    private void angelica$startF3Batching(int width, int height, CallbackInfo ci) {
        FontRendererAccessor fra = (FontRendererAccessor) (Object) fontrenderer;
        fra.angelica$getBatcher().beginBatch();

        angelica$ptrOffset = 0;
    }

    @Inject(
        method = "renderHUDText",
        at = @At("RETURN"), remap = false)
    private void angelica$endF3Batching(int width, int height, CallbackInfo ci) {
        angelica$data.limit(angelica$ptrOffset);

        GLStateManager.glColor4f(0.3137f, 0.3137f, 0.3137f, 0.5647f); // 0x90505050
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        TessellatorStreamingDrawer.drawPacked(
            angelica$data,
            GL11.GL_QUADS,
            VertexFlags.POSITION_BIT,
            angelica$ptrOffset / 12
        );
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glDisable(GL11.GL_BLEND);

        // Font rendering has to go AFTER batched rects
        FontRendererAccessor fra = (FontRendererAccessor) (Object) fontrenderer;
        fra.angelica$getBatcher().endBatch();
    }
}
