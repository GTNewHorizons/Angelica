package com.gtnewhorizons.angelica.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.textures.atlas.AtlasPackPolicy;

import net.coderbot.iris.gui.element.IrisGuiSlot;
import net.coderbot.iris.gui.element.widget.IrisButton;

public class AtlasPackScreen extends GuiScreen {

    private static final String ENABLED_PREFIX = EnumChatFormatting.GREEN + "[x] " + EnumChatFormatting.WHITE;
    private static final String DISABLED_PREFIX = EnumChatFormatting.DARK_GRAY + "[ ] " + EnumChatFormatting.GRAY;

    private final GuiScreen parent;
    private final String title = I18n.format("options.angelica.atlasPacks.title");
    private final String info = I18n.format("options.angelica.atlasPacks.info");
    private final String builtinSuffix = " - " + I18n.format("options.angelica.atlasPacks.builtin");

    private final List<String> packs;
    private boolean dirty;

    private PackRowList rowList;

    public AtlasPackScreen(GuiScreen parent) {
        this.parent = parent;
        this.packs = AtlasPackPolicy.knownPackClasses();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.rowList = new PackRowList();

        this.buttonList.add(new IrisButton(this.width / 2 - 75, this.height - 27, 150, 20, I18n.format("gui.done"), button -> this.onClose()));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void onClose() {
        if (this.dirty) {
            ConfigurationManager.save(AngelicaConfig.class);
            this.dirty = false;
        }
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.rowList.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(this.fontRendererObj, this.title, this.width / 2, 8, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, this.info, this.width / 2, 20, 0xA0A0A0);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!this.rowList.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == -1 || !this.rowList.mouseReleased(mouseX, mouseY, Mouse.getEventButton())) {
            super.mouseMovedOrUp(mouseX, mouseY, state);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled && button instanceof IrisButton irisButton) {
            irisButton.onPress();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.onClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private class PackRowList extends IrisGuiSlot {

        PackRowList() {
            super(AtlasPackScreen.this.mc, AtlasPackScreen.this.width, AtlasPackScreen.this.height, 36, AtlasPackScreen.this.height - 44, 18);
            this.setRenderBackground(false);
        }

        @Override
        protected int getSize() {
            return packs.size();
        }

        @Override
        protected boolean isSelected(int index) {
            return false;
        }

        @Override
        protected void drawBackground() {}

        @Override
        public int getListWidth() {
            return Math.min(400, AtlasPackScreen.this.width - 40);
        }

        @Override
        protected boolean elementClicked(int index, boolean doubleClick, int mouseX, int mouseY, int button) {
            if (button != 0 || index >= packs.size()) {
                return false;
            }

            final var name = packs.get(index);
            AtlasPackPolicy.setTrusted(name, !AtlasPackPolicy.isTrusted(name));
            dirty = true;
            return true;
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, Tessellator tessellator, int mouseX, int mouseY) {
            final var name = packs.get(index);
            final var trusted = AtlasPackPolicy.isTrusted(name);

            int simpleStart = name.lastIndexOf('.') + 1;
            final var packageName = simpleStart > 0 ? name.substring(0, simpleStart - 1) : "";
            var simpleName = name.substring(simpleStart);
            final int innerStart = simpleName.lastIndexOf('$') + 1;
            if (innerStart > 0) {
                simpleName = simpleName.substring(innerStart);
            }

            var label = (trusted ? ENABLED_PREFIX : DISABLED_PREFIX) + simpleName + EnumChatFormatting.DARK_GRAY + " (" + packageName + ")";
            if (AtlasPackPolicy.isBuiltIn(name)) {
                label += builtinSuffix;
            }

            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(label, this.getListWidth() - 8), x, y + 5, 0xFFFFFF);
        }
    }
}
