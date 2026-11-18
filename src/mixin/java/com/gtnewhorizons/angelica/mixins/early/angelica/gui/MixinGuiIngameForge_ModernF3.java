package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenOverlay;
import com.gtnewhorizons.angelica.debug.screen.ForgeDebugPlaceholders;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(GuiIngameForge.class)
public abstract class MixinGuiIngameForge_ModernF3 {

    @Shadow(remap = false)
    private FontRenderer fontrenderer;

    @Shadow(remap = false)
    private boolean pre(ElementType type) {
        return false;
    }

    @Shadow(remap = false)
    private void post(ElementType type) {}

    @Unique
    private boolean angelica$forgeLinesSkipped;

    @Inject(method = "renderHUDText", remap = false, at = @At("HEAD"))
    private void angelica$resetDebugLatch(int width, int height, CallbackInfo ci) {
        angelica$forgeLinesSkipped = false;
        DebugScreen.syncOverlayVisible();
    }

    @ModifyExpressionValue(
        method = "renderHUDText",
        remap = false,
        require = 1,
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;showDebugInfo:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0,
            remap = true))
    private boolean angelica$skipForgeDebugLines(boolean showDebugInfo) {
        final boolean show = showDebugInfo;
        angelica$forgeLinesSkipped = show && DebugScreen.isGroupedActive() && DebugScreen.ENTRIES != null;
        return show && !angelica$forgeLinesSkipped;
    }

    @Inject(
        method = "renderHUDText",
        remap = false,
        require = 1,
        at = @At(
            value = "INVOKE",
            target = "net/minecraftforge/client/event/RenderGameOverlayEvent$Text.<init>(Lnet/minecraftforge/client/event/RenderGameOverlayEvent;Ljava/util/ArrayList;Ljava/util/ArrayList;)V",
            remap = false))
    private void angelica$afterDebugBlock(int width, int height, CallbackInfo ci, @Local(ordinal = 0) ArrayList<String> left, @Local(ordinal = 1) ArrayList<String> right) {
        final boolean skipped = angelica$forgeLinesSkipped;
        final boolean ran;
        if (skipped) {
            ran = !pre(ElementType.DEBUG);
            if (ran) {
                final Minecraft mc = Minecraft.getMinecraft();
                mc.mcProfiler.startSection("debug");
                ForgeDebugPlaceholders.add(left, right, angelica$chunkLoaded(mc), mc.entityRenderer != null && mc.entityRenderer.isShaderActive());
                mc.mcProfiler.endSection();
                post(ElementType.DEBUG);
            }
        } else {
            ran = DebugScreen.isOverlayVisible() && !left.isEmpty();
        }
        if (DebugScreen.isOverlayIdle()) return;
        DebugScreenOverlay.INSTANCE.afterDebugBlock(ran, skipped);
    }

    @Unique
    private static boolean angelica$chunkLoaded(Minecraft mc) {
        if (mc.theWorld == null) return false;
        final int x = MathHelper.floor_double(mc.thePlayer.posX);
        final int y = MathHelper.floor_double(mc.thePlayer.posY);
        final int z = MathHelper.floor_double(mc.thePlayer.posZ);
        return mc.theWorld.blockExists(x, y, z);
    }

    @ModifyExpressionValue(
        method = "renderHUDText",
        remap = false,
        require = 1,
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z",
            remap = false))
    private boolean angelica$drawDebugScreen(boolean canceled, @Local RenderGameOverlayEvent.Text event, @Local(argsOnly = true, ordinal = 0) int width, @Local(argsOnly = true, ordinal = 1) int height) {
        if (DebugScreen.isOverlayIdle()) return canceled;
        return DebugScreenOverlay.INSTANCE.onTextEvent(event, fontrenderer, width, height, canceled);
    }

    @Redirect(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 0,
            remap = true))
    private int renderBackgroundLeft(FontRenderer fontRenderer, String text, int x, int y, int color) {
        Gui.drawRect(x - 1, y - 1, x + fontRenderer.getStringWidth(text) + 1, y + fontRenderer.FONT_HEIGHT, 0x90505050);
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
        Gui.drawRect(x - 1, y - 1, x + fontRenderer.getStringWidth(text) + 1, y + fontRenderer.FONT_HEIGHT, 0x90505050);
        return fontRenderer.drawString(text, x, y, 0xe0e0e0);
    }
}
