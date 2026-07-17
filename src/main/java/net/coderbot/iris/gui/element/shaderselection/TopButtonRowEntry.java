package net.coderbot.iris.gui.element.shaderselection;

import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.element.IrisElementRow;
import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import java.util.function.Function;

public class TopButtonRowEntry extends BaseEntry {

    private static String nonePresentLabel()   { return I18n.format("options.iris.shaders.nonePresent"); }
    private static String shadersDisabledLabel() { return I18n.format("options.iris.shaders.disabled"); }
    private static String shadersEnabledLabel()  { return I18n.format("options.iris.shaders.enabled"); }

    private final ShaderPackSelectionList shaderPackSelectionList;
    private final IrisElementRow buttons = new IrisElementRow();
    private final EnableShadersButtonElement enableDisableButton;

    public boolean allowEnableShadersButton = true;
    public boolean shadersEnabled;

    public TopButtonRowEntry(ShaderPackSelectionList list, boolean shadersEnabled) {
        super(list);
        this.shaderPackSelectionList = list;
        this.shadersEnabled = shadersEnabled;
        this.enableDisableButton = new EnableShadersButtonElement(
            getEnableDisableLabel(),
            button -> {
                if (this.allowEnableShadersButton) {
                    setShadersEnabled(!this.shadersEnabled);
                    GuiUtil.playButtonClickSound();
                    return true;
                }

                return false;
            });
        this.buttons.add(this.enableDisableButton, 0);
    }

    public void setShadersEnabled(boolean shadersEnabled) {
        this.shadersEnabled = shadersEnabled;
        this.enableDisableButton.text = getEnableDisableLabel();
        this.shaderPackSelectionList.getScreen().refreshScreenSwitchButton();
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int listWidth, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        this.buttons.setWidth(this.enableDisableButton, listWidth);
        this.enableDisableButton.centerX = x + (int)(listWidth * 0.5);

        this.buttons.drawScreen(x - 2, y - 3, 18, mouseX, mouseY, 0, isMouseOver);
    }

    private String getEnableDisableLabel() {
        return this.allowEnableShadersButton ? this.shadersEnabled ? shadersEnabledLabel() : shadersDisabledLabel() : nonePresentLabel();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.buttons.mouseClicked(mouseX, mouseY, button);
    }

    // Renders the label at an offset as to not look misaligned with the rest of the menu
    public static class EnableShadersButtonElement extends IrisElementRow.TextButtonElement {
        private int centerX;

        public EnableShadersButtonElement(String text, Function<IrisElementRow.TextButtonElement, Boolean> onClick) {
            super(text, onClick);
        }

        @Override
        public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
            final int textX = this.centerX - (int)(this.font.getStringWidth(this.text) * 0.5);
            final int textY = y + (int)((height - 8) * 0.5);

            this.font.drawStringWithShadow(this.text, textX, textY, 0xFFFFFF);
        }
    }
}
