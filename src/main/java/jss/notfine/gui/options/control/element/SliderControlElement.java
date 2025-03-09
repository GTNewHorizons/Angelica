package jss.notfine.gui.options.control.element;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class SliderControlElement extends NotFineControlElement<Integer> {
    private final ControlValueFormatter formatter;
    private final int min, max, interval;

    private float value;
    private boolean mousePressed;
    private boolean dirty = false;

    public SliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
        super(option, dim);
        this.min = min;
        this.max = max;
        this.interval = interval;
        this.formatter = formatter;
        value = option.getValue();
        //Normalize value
        value = MathHelper.clamp_float((value - min) / (max - min), 0f, 1f);
    }

    @Override
    public String getLabel() {
        return super.getLabel() + formatter.format(option.getValue());
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
        if(dirty) {
            onOptionValueChanged();
            dirty = false;
        }
    }

    private void updateSlider(int mouseX) {
        value = (float)(mouseX - (xPosition + 4)) / (float)(width - 8);
        //Clamp normalized value
        value = MathHelper.clamp_float(value, 0f, 1f);
        //Un-normalize value
        value = min + (max - min) * MathHelper.clamp_float(value, 0f, 1f);
        //Clamp value
        value = MathHelper.clamp_float(value, min, max);
        //Snap value
        if(interval > 0) {
            value = interval * (float)Math.round(value / interval);
        }
        //Commit value
        option.setValue((int)value);
        //Normalize value
        value = MathHelper.clamp_float((value - min) / (max - min), 0f, 1f);

        dirty = true;
    }

}
