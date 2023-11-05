package me.jellysquid.mods.sodium.client.gui.options.control;

import me.jellysquid.mods.sodium.client.gui.options.FormattedTextProvider;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.TextProvider;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final Text[] names;

    public CyclingControl(Option<T> option, Class<T> enumType) {
        this(option, enumType, enumType.getEnumConstants());
    }

    public CyclingControl(Option<T> option, Class<T> enumType, Text[] names) {
        T[] universe = enumType.getEnumConstants();

        Validate.isTrue(universe.length == names.length, "Mismatch between universe length and names array length");
        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
        this.allowedValues = universe;
        this.names = names;
    }

    @Deprecated
    public CyclingControl(Option<T> option, Class<T> enumType, String[] names) {
        this(option, enumType, Arrays.stream(names).map(LiteralText::new).toArray(Text[]::new));
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        T[] universe = enumType.getEnumConstants();

        this.option = option;
        this.allowedValues = allowedValues;
        this.names = new Text[universe.length];

        for (int i = 0; i < this.names.length; i++) {
            Text name;
            T value = universe[i];

            if (value instanceof TextProvider) {
                name = new LiteralText(((TextProvider)value).getLocalizedName());
            } else if(value instanceof FormattedTextProvider) {
                name = ((FormattedTextProvider)value).getLocalizedName();
            } else {
                name = new LiteralText(value.name());
            }

            this.names[i] = name;
        }
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public ControlElement<T> createElement(Dim2i dim) {
        return new CyclingControlElement<>(this.option, dim, this.allowedValues, this.names);
    }

    @Override
    public int getMaxWidth() {
        return 70;
    }

    private static class CyclingControlElement<T extends Enum<T>> extends ControlElement<T> {
        private final T[] allowedValues;
        private final Text[] names;
        private int currentIndex;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, Text[] names) {
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
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
            super.render(matrixStack, mouseX, mouseY, delta);

            Enum<T> value = this.option.getValue();
            Text name = this.names[value.ordinal()];

            int strWidth = this.getTextWidth(name);
            this.drawText(matrixStack, name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                this.currentIndex = (this.option.getValue().ordinal() + 1) % this.allowedValues.length;
                this.option.setValue(this.allowedValues[this.currentIndex]);
                this.playClickSound();

                return true;
            }

            return false;
        }
    }
}
