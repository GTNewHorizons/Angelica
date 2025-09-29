package com.gtnewhorizons.angelica.client.gui;

import lombok.AllArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;
import java.util.function.Function;

public class SliderClone extends GuiButton {

    private float sliderPercentage;
    public final String tooltipKey;
    public boolean mouseDragging;
    private Option option;
    private Consumer<Float> valueSetter;
    private Function<Float, String> displayStringFormatter;

    @AllArgsConstructor
    public static class Option {
        float min;
        float max;
        float step;

        float denormalize(float alpha) {
            float lerp = min * (1 - alpha) + max * alpha;
            return Math.round(lerp / step) * step;
        }
        float normalize(float value) {
            return (value - min) / (max - min);
        }
    }

    public SliderClone(int x, int y, int width, int height, Option option, float initialValue, Consumer<Float> setter, Function<Float, String> formatter, String tooltipKey) {
        super(0, x, y, width, height, "");
        this.option = option;
        this.sliderPercentage = option.normalize(initialValue);
        this.displayString = formatter.apply(initialValue);
        this.valueSetter = setter;
        this.displayStringFormatter = formatter;
        this.tooltipKey = tooltipKey;
    }

    /**
     * Returns 0 if the button is disabled, 1 if the mouse is NOT hovering over this button and 2 if it IS hovering over
     * this button.
     */
    public int getHoverState(boolean mouseOver) {
        return 0;
    }

    /**
     * Fired when the mouse button is dragged. Equivalent of MouseListener.mouseDragged(MouseEvent e).
     */
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            if (this.mouseDragging) {
                this.sliderPercentage = (float)(mouseX - (this.xPosition + 4)) / (float)(this.width - 8);

                if (this.sliderPercentage < 0.0F) {
                    this.sliderPercentage = 0.0F;
                }

                if (this.sliderPercentage > 1.0F) {
                    this.sliderPercentage = 1.0F;
                }

                float f = this.option.denormalize(this.sliderPercentage);
                valueSetter.accept(f);
                this.sliderPercentage = this.option.normalize(f);
                this.displayString = displayStringFormatter.apply(f);
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexturedModalRect(this.xPosition + (int)(this.sliderPercentage * (float)(this.width - 8)), this.yPosition, 0, 66, 4, 20);
            this.drawTexturedModalRect(this.xPosition + (int)(this.sliderPercentage * (float)(this.width - 8)) + 4, this.yPosition, 196, 66, 4, 20);
        }
    }

    /**
     * Returns true if the mouse has been pressed on this control. Equivalent of MouseListener.mousePressed(MouseEvent e).
     */
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.sliderPercentage = (float)(mouseX - (this.xPosition + 4)) / (float)(this.width - 8);

            if (this.sliderPercentage < 0.0F) {
                this.sliderPercentage = 0.0F;
            }

            if (this.sliderPercentage > 1.0F) {
                this.sliderPercentage = 1.0F;
            }

            float f = this.option.denormalize(this.sliderPercentage);
            valueSetter.accept(f);
            this.sliderPercentage = this.option.normalize(f);
            this.displayString = displayStringFormatter.apply(f);
            this.mouseDragging = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Fired when the mouse button is released. Equivalent of MouseListener.mouseReleased(MouseEvent e).
     */
    public void mouseReleased(int mouseX, int mouseY) {
        this.mouseDragging = false;
    }
}
