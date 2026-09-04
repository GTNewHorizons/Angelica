package com.gtnewhorizons.angelica.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfig;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityTypeEntry;

import net.coderbot.iris.gui.element.IrisGuiSlot;
import net.coderbot.iris.gui.element.widget.IrisButton;

public class DynamicLightsEntityScreen extends GuiScreen {

    private static final String ENABLED_PREFIX = EnumChatFormatting.GREEN + "[x] " + EnumChatFormatting.WHITE;
    private static final String DISABLED_PREFIX = EnumChatFormatting.DARK_GRAY + "[ ] " + EnumChatFormatting.GRAY;

    private static List<EntityTypeEntry> entityTypes;

    private final GuiScreen parent;
    private final String title = I18n.format("options.dynamiclights.entities.title");
    private final String info = I18n.format("options.dynamiclights.entities.info");

    private List<EntityTypeEntry> displayed;
    private String countLabel;
    private String search = "";
    private boolean dirty;

    private GuiTextField searchBox;
    private EntityRowList rowList;
    private IrisButton toggleButton;

    public DynamicLightsEntityScreen(GuiScreen parent) {
        this.parent = parent;
        this.refilter();
    }

    private static List<EntityTypeEntry> entityTypes() {
        if (entityTypes == null) {
            entityTypes = EntityLightConfig.getAllEntityTypes();
        }
        return entityTypes;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.searchBox = new GuiTextField(this.fontRendererObj, this.width / 2 - 150, 30, 300, 20);
        this.searchBox.setMaxStringLength(64);
        this.searchBox.setText(this.search);
        this.rowList = new EntityRowList();

        this.toggleButton = new IrisButton(this.width / 2 - 154, this.height - 27, 150, 20, "", button -> this.toggleVisible());
        this.updateToggleButton();

        this.buttonList.add(this.toggleButton);
        this.buttonList.add(new IrisButton(this.width / 2 + 4, this.height - 27, 150, 20, I18n.format("gui.done"), button -> this.onClose()));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void onClose() {
        if (this.dirty) {
            EntityLightConfig.save();
            this.dirty = false;
        }
        this.mc.displayGuiScreen(this.parent);
    }

    private void refilter() {
        final var filter = DynamicLightsEntityFilter.parse(this.search);
        if (filter.isMatchAll()) {
            this.displayed = entityTypes();
        } else {
            final var matches = new ArrayList<EntityTypeEntry>();
            for (final var entry : entityTypes()) {
                if (filter.matches(entry.getSearchName(), entry.getSearchModId(), EntityLightConfig.isEntityTypeEnabled(entry.getEntityClass()))) {
                    matches.add(entry);
                }
            }
            this.displayed = matches;
        }
        this.countLabel = this.displayed.size() + " / " + entityTypes().size();
        this.updateToggleButton();
    }

    private boolean anyDisplayedEnabled() {
        for (final var entry : this.displayed) {
            if (EntityLightConfig.isEntityTypeEnabled(entry.getEntityClass())) {
                return true;
            }
        }
        return false;
    }

    private void updateToggleButton() {
        if (this.toggleButton == null) {
            return;
        }

        final var subset = this.displayed.size() != entityTypes().size();
        final var key = this.anyDisplayedEnabled()
            ? (subset ? "options.dynamiclights.entities.disableshown" : "options.dynamiclights.entities.disableall")
            : (subset ? "options.dynamiclights.entities.enableshown" : "options.dynamiclights.entities.enableall");

        this.toggleButton.displayString = I18n.format(key);
        this.toggleButton.enabled = !this.displayed.isEmpty();
    }

    private void toggleVisible() {
        final var enable = !this.anyDisplayedEnabled();
        for (final var entry : this.displayed) {
            EntityLightConfig.setEntityTypeEnabled(entry.getEntityClass(), enable);
        }
        this.dirty = true;
        this.refilter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.rowList.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(this.fontRendererObj, this.title, this.width / 2, 8, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, this.info, this.width / 2, 20, 0xA0A0A0);
        this.searchBox.drawTextBox();
        if (!this.searchBox.isFocused() && this.search.isEmpty()) {
            this.drawString(this.fontRendererObj, I18n.format("options.dynamiclights.entities.search"), this.width / 2 - 144, 36, 0x808080);
        }
        drawCenteredString(this.fontRendererObj, this.countLabel, this.width / 2, this.height - 39, 0x808080);
    }

    @Override
    public void updateScreen() {
        this.searchBox.updateCursorCounter();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!this.rowList.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
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
        if (this.searchBox.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return;
            }
            this.searchBox.textboxKeyTyped(typedChar, keyCode);
            if (!this.searchBox.getText().equals(this.search)) {
                this.search = this.searchBox.getText();
                this.refilter();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.onClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private class EntityRowList extends IrisGuiSlot {

        EntityRowList() {
            super(DynamicLightsEntityScreen.this.mc, DynamicLightsEntityScreen.this.width, DynamicLightsEntityScreen.this.height, 56, DynamicLightsEntityScreen.this.height - 44, 18);
            this.setRenderBackground(false);
        }

        @Override
        protected int getSize() {
            return displayed.size();
        }

        @Override
        protected boolean isSelected(int index) {
            return false;
        }

        @Override
        protected void drawBackground() {}

        @Override
        public int getListWidth() {
            return Math.min(400, DynamicLightsEntityScreen.this.width - 40);
        }

        @Override
        protected boolean elementClicked(int index, boolean doubleClick, int mouseX, int mouseY, int button) {
            if (button != 0 || index >= displayed.size()) {
                return false;
            }

            final var entry = displayed.get(index);
            EntityLightConfig.setEntityTypeEnabled(entry.getEntityClass(), !EntityLightConfig.isEntityTypeEnabled(entry.getEntityClass()));
            dirty = true;
            updateToggleButton();
            return true;
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, Tessellator tessellator, int mouseX, int mouseY) {
            final var entry = displayed.get(index);
            final var enabled = EntityLightConfig.isEntityTypeEnabled(entry.getEntityClass());
            final var label = (enabled ? ENABLED_PREFIX : DISABLED_PREFIX) + entry.getDisplayName() + EnumChatFormatting.DARK_GRAY + " (" + entry.getModId() + ")";

            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(label, this.getListWidth() - 8), x, y + 5, 0xFFFFFF);
        }
    }
}
