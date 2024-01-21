package me.jellysquid.mods.sodium.client.gui.options.control.element;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.utils.Rect2i;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.util.MathHelper;

public class SodiumControlElementFactory implements ControlElementFactory {
    @Override
    public <T extends Enum<T>> ControlElement cyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
        return new CyclingControlElement(option, dim, allowedValues, names);
    }

    @Override
    public ControlElement sliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
        return new SliderControlElement(option, dim, min, max, interval, formatter);
    }

    @Override
    public ControlElement tickBoxElement(Option<Boolean> option, Dim2i dim) {
        return new TickBoxControlElement(option, dim);
    }

    private static class CyclingControlElement<T extends Enum<T>> extends SodiumControlElement<T> {
        private final T[] allowedValues;
        private final String[] names;
        private int currentIndex;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
            this.currentIndex = 0;

            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == option.getValue()) {
                    this.currentIndex = i;
                    break;
                }
            }
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            Enum<T> value = this.option.getValue();
            String name = this.names[value.ordinal()];

            int strWidth = this.getTextWidth(name);
            this.drawString(name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(AngelicaConfig.enableReesesSodiumOptions) {
                if (this.option.isAvailable() && this.dim.containsCursor(mouseX, mouseY) && (button == 0 || button == 1)) {
                    this.currentIndex = Math.floorMod(this.option.getValue().ordinal() + (button == 0 ? 1 : -1), this.allowedValues.length);
                    this.option.setValue(this.allowedValues[this.currentIndex]);
                    this.playClickSound();

                    return true;
                }
            } else {
                if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                    this.currentIndex = (this.option.getValue().ordinal() + 1) % this.allowedValues.length;
                    this.option.setValue(this.allowedValues[this.currentIndex]);
                    this.playClickSound();

                    return true;
                }
            }

            return false;
        }
    }

    private static class SliderControlElement extends SodiumControlElement<Integer> {
        private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;

        private final Rect2i sliderBounds;
        private final ControlValueFormatter formatter;

        private final int min;
        private final int range;
        private final int interval;

        private double thumbPosition;

        public SliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
            super(option, dim);

            this.min = min;
            this.range = max - min;
            this.interval = interval;
            this.thumbPosition = this.getThumbPositionForValue(option.getValue());
            this.formatter = formatter;

            this.sliderBounds = new Rect2i(dim.getLimitX() - 96, dim.getCenterY() - 5, 90, 10);
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            if (this.option.isAvailable() && this.hovered) {
                this.renderSlider();
            } else {
                this.renderStandaloneValue();
            }
        }

        private void renderStandaloneValue() {
            int sliderX = this.sliderBounds.getX();
            int sliderY = this.sliderBounds.getY();
            int sliderWidth = this.sliderBounds.getWidth();
            int sliderHeight = this.sliderBounds.getHeight();

            String label = this.formatter.format(this.option.getValue());
            int labelWidth = this.font.getStringWidth(label);

            this.drawString(label, sliderX + sliderWidth - labelWidth, sliderY + (sliderHeight / 2) - 4, 0xFFFFFFFF);
        }

        private void renderSlider() {
            int sliderX = this.sliderBounds.getX();
            int sliderY = this.sliderBounds.getY();
            int sliderWidth = this.sliderBounds.getWidth();
            int sliderHeight = this.sliderBounds.getHeight();

            this.thumbPosition = this.getThumbPositionForValue(option.getValue());

            double thumbOffset = MathHelper.clamp_double((double) (this.getIntValue() - this.min) / this.range * sliderWidth, 0, sliderWidth);

            double thumbX = sliderX + thumbOffset - THUMB_WIDTH;
            double trackY = sliderY + (sliderHeight / 2) - ((double) TRACK_HEIGHT / 2);

            this.drawRect(thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + sliderHeight, 0xFFFFFFFF);
            this.drawRect(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, 0xFFFFFFFF);

            String label = String.valueOf(this.getIntValue());

            int labelWidth = this.font.getStringWidth(label);

            this.drawString(label, sliderX - labelWidth - 6, sliderY + (sliderHeight / 2) - 4, 0xFFFFFFFF);
        }

        public int getIntValue() {
            return this.min + (this.interval * (int) Math.round(this.getSnappedThumbPosition() / this.interval));
        }

        public double getSnappedThumbPosition() {
            return this.thumbPosition / (1.0D / this.range);
        }

        public double getThumbPositionForValue(int value) {
            return (value - this.min) * (1.0D / this.range);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.sliderBounds.contains((int) mouseX, (int) mouseY)) {
                this.setValueFromMouse(mouseX);

                return true;
            }

            return false;
        }

        private void setValueFromMouse(double d) {
            this.setValue((d - (double) this.sliderBounds.getX()) / (double) this.sliderBounds.getWidth());
        }

        private void setValue(double d) {
            this.thumbPosition = MathHelper.clamp_double(d, 0.0D, 1.0D);

            int value = this.getIntValue();

            if (this.option.getValue() != value) {
                this.option.setValue(value);
            }
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && this.sliderBounds.contains((int) mouseX, (int) mouseY)) {
                this.setValueFromMouse(mouseX);

                return true;
            }

            return false;
        }
    }

    private static class TickBoxControlElement extends SodiumControlElement<Boolean> {
        private final Rect2i button;

        public TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
            super(option, dim);

            this.button = new Rect2i(dim.getLimitX() - 16, dim.getCenterY() - 5, 10, 10);
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            final int x = this.button.getX();
            final int y = this.button.getY();
            final int w = x + this.button.getWidth();
            final int h = y + this.button.getHeight();

            final boolean enabled = this.option.isAvailable();
            final boolean ticked = enabled && this.option.getValue();

            final int color;

            if (enabled) {
                color = ticked ? 0xFF94E4D3 : 0xFFFFFFFF;
            } else {
                color = 0xFFAAAAAA;
            }

            if (ticked) {
                this.drawRect(x + 2, y + 2, w - 2, h - 2, color);
            }

            this.drawRectOutline(x, y, w, h, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                this.option.setValue(!this.option.getValue());
                this.playClickSound();

                return true;
            }

            return false;
        }

        protected void drawRectOutline(int x, int y, int w, int h, int color) {
            final float a = (float) (color >> 24 & 255) / 255.0F;
            final float r = (float) (color >> 16 & 255) / 255.0F;
            final float g = (float) (color >> 8 & 255) / 255.0F;
            final float b = (float) (color & 255) / 255.0F;

            this.drawQuads(vertices -> {
                addQuad(vertices, x, y, w, y + 1, a, r, g, b);
                addQuad(vertices, x, h - 1, w, h, a, r, g, b);
                addQuad(vertices, x, y, x + 1, h, a, r, g, b);
                addQuad(vertices, w - 1, y, w, h, a, r, g, b);
            });
        }
    }

}
