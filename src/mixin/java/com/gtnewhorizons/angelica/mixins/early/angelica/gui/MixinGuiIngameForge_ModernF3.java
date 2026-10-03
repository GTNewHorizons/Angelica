package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge_ModernF3 {

    @Shadow(remap = false)
    private FontRenderer fontrenderer;

    @WrapOperation(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z"))
    private boolean angelica$renderDebugPanels(EventBus bus, Event event, Operation<Boolean> original,
        @Local(argsOnly = true, ordinal = 0) int width, @Local(argsOnly = true, ordinal = 1) int height) {
        final boolean canceled = original.call(bus, event);
        final Minecraft mc = Minecraft.getMinecraft();
        if (canceled || !AngelicaConfig.modernizeF3Screen || !mc.gameSettings.showDebugInfo
            || !(event instanceof RenderGameOverlayEvent.Text text) || text.left.isEmpty()) return canceled;

        final int columnWidth = Math.max(20, (width - 16) / 2);
        final Map<String, List<String>> left = angelica$groupDebugLines(text.left, false, columnWidth);
        final Map<String, List<String>> right = angelica$groupDebugLines(text.right, true, columnWidth);
        left.get("target").addAll(right.remove("target"));
        final int contentHeight = Math.max(angelica$debugColumnHeight(left), angelica$debugColumnHeight(right));
        final int availableHeight = height - 8
            - (((IGameSettingsExt) mc.gameSettings).angelica$showFpsGraph() ? 62 + fontrenderer.FONT_HEIGHT : 0);
        final float scale = Math.min(1.0F, Math.max(1, availableHeight) / (float) Math.max(1, contentHeight));
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glScalef(scale, scale, 1.0F);
            angelica$drawDebugColumn(left, (int) (width / scale), false);
            angelica$drawDebugColumn(right, (int) (width / scale), true);
        } finally {
            GLStateManager.glPopMatrix();
        }
        return true;
    }

    @Unique
    private Map<String, List<String>> angelica$groupDebugLines(List<String> lines, boolean right, int columnWidth) {
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String group : new String[] { "game", "position", "world", "memory", "system", "rendering", "other", "target" }) {
            groups.put(group, new ArrayList<>());
        }
        final List<String> brandings = FMLCommonHandler.instance().getBrandings(false);
        boolean rendererSection = false;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                rendererSection = false;
                continue;
            }
            final String plain = EnumChatFormatting.getTextWithoutFormattingCodes(line);
            String group = "other";
            if (plain.startsWith("Angelica ")) rendererSection = true;
            if (plain.startsWith("XYZ:") || plain.startsWith("Block:") || plain.startsWith("Chunk:")
                || plain.startsWith("Facing:")) {
                group = "position";
            } else if (plain.startsWith("lc:") || plain.startsWith("ws:") || plain.startsWith("Biome:")
                || plain.startsWith("Dimension:") || plain.startsWith("Light:") || plain.startsWith("Height:")) {
                group = "world";
            } else if (plain.startsWith("Used memory:") || plain.startsWith("Allocated memory:")
                || plain.startsWith("Allocation rate:") || plain.startsWith("Direct Buffers:")) {
                group = "memory";
            } else if (plain.startsWith("Java:") || plain.startsWith("GPU:") || plain.startsWith("OpenGL:")
                || plain.startsWith("CPU Cores:") || plain.startsWith("OS:")) {
                group = "system";
            } else if (plain.startsWith("Minecraft ") || brandings.contains(plain)) {
                group = "game";
            } else if (right && (plain.startsWith("meta:") || (i + 1 < lines.size() && lines.get(i + 1) != null
                && EnumChatFormatting.getTextWithoutFormattingCodes(lines.get(i + 1)).startsWith("meta:")))) {
                group = "target";
            } else if (rendererSection || plain.startsWith("C:") || plain.startsWith("E:") || plain.startsWith("P:")
                || plain.startsWith("Integrated server @") || plain.startsWith("Dynamic Light")
                || plain.startsWith("[" + Iris.MODNAME) || plain.startsWith("animationsMode:")) {
                group = "rendering";
            }
            groups.get(group).addAll(fontrenderer.listFormattedStringToWidth(line, columnWidth));
        }
        return groups;
    }

    @Unique
    private int angelica$debugColumnHeight(Map<String, List<String>> groups) {
        int height = 0;
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            final int lineCount = group.getKey().equals("target") ? 3 : group.getValue().size();
            if (lineCount > 0) height += (lineCount + 1) * (fontrenderer.FONT_HEIGHT + 1) + 18;
        }
        return height;
    }

    @Unique
    private void angelica$drawDebugColumn(Map<String, List<String>> groups, int width, boolean right) {
        final int lineHeight = fontrenderer.FONT_HEIGHT + 1;
        int y = 4;
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            final List<String> lines = group.getValue();
            final boolean target = group.getKey().equals("target");
            final int panelHeight = ((target ? 3 : lines.size()) + 1) * lineHeight + 12;
            if (lines.isEmpty()) {
                if (target) y += panelHeight + 6;
                continue;
            }
            final String title = I18n.format("angelica.debug." + group.getKey());
            int panelWidth = fontrenderer.getStringWidth(title);
            for (String line : lines) panelWidth = Math.max(panelWidth, fontrenderer.getStringWidth(line));
            panelWidth += 6;
            final int x = right ? width - panelWidth - 4 : 4;
            angelica$drawDebugPanel(x, y, panelWidth, panelHeight, 0x90505050, true);
            angelica$drawDebugPanel(x, y, panelWidth, lineHeight + 6, 0xA0606060, false);
            fontrenderer.drawString(title, x + 3, y + (lineHeight + 6 - fontrenderer.FONT_HEIGHT) / 2 + 1, 0xFFFFFF);
            final boolean scaleText = target && lines.size() > 3;
            if (scaleText) GLStateManager.glPushMatrix();
            try {
                if (scaleText) {
                    GLStateManager.glTranslatef(x + 3, y + lineHeight + 10, 0.0F);
                    final float textScale = 3.0F / lines.size();
                    GLStateManager.glScalef(textScale, textScale, 1.0F);
                }
                int textY = scaleText ? 0 : y + lineHeight + 10;
                for (String line : lines) {
                    fontrenderer.drawString(line, scaleText ? 0 : x + 3, textY, 0xE0E0E0);
                    textY += lineHeight;
                }
            } finally {
                if (scaleText) GLStateManager.glPopMatrix();
            }
            y += panelHeight + 6;
        }
    }

    @Unique
    private void angelica$drawDebugPanel(int x, int y, int width, int height, int color, boolean roundBottom) {
        Gui.drawRect(x + 3, y, x + width - 3, y + 1, color);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 3, color);
        Gui.drawRect(x, y + 3, x + width, y + height - (roundBottom ? 3 : 0), color);
        if (roundBottom) {
            Gui.drawRect(x + 1, y + height - 3, x + width - 1, y + height - 1, color);
            Gui.drawRect(x + 3, y + height - 1, x + width - 3, y + height, color);
        }
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
