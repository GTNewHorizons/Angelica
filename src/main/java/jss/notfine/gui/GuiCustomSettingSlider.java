package jss.notfine.gui;

import jss.notfine.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class GuiCustomSettingSlider extends GuiButton {

    private final Settings linkedSetting;
    private float value;
    public boolean mousePressed;

    public GuiCustomSettingSlider(int xPosition, int yPosition, Settings setting) {
        super(setting.ordinal(), xPosition, yPosition, 150, 20, "");
        linkedSetting = setting;
        value = setting.getValueNormalized();
        displayString = setting.getLocalization();
    }

    @Override
    public int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if(visible) {
            if(mousePressed) {
                updateSlider(mouseX);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawTexturedModalRect(xPosition + (int)(value * (float)(width - 8)), yPosition, 0, 66, 4, 20);
            drawTexturedModalRect(xPosition + (int)(value * (float)(width - 8)) + 4, yPosition, 196, 66, 4, 20);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            updateSlider(mouseX);
            mousePressed = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        mousePressed = false;
    }

    private void updateSlider(int mouseX) {
        value = (float)(mouseX - (xPosition + 4)) / (float)(width - 8);
        value = MathHelper.clamp_float(value, 0f, 1f);

        linkedSetting.setValueNormalized(value);
        value = linkedSetting.getValueNormalized();

        displayString = linkedSetting.getLocalization();
    }

}
