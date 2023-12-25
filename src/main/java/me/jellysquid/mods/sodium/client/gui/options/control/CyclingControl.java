package me.jellysquid.mods.sodium.client.gui.options.control;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.resources.I18n;
import org.apache.commons.lang3.Validate;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final String[] names;

    public CyclingControl(Option<T> option, Class<T> enumType) {
        this(option, enumType, enumType.getEnumConstants());
    }

    public CyclingControl(Option<T> option, Class<T> enumType, String[] names) {
        T[] universe = enumType.getEnumConstants();

        Validate.isTrue(universe.length == names.length, "Mismatch between universe length and names array length");
        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
        this.allowedValues = universe;
        this.names = names;
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        T[] universe = enumType.getEnumConstants();

        this.option = option;
        this.allowedValues = allowedValues;
        this.names = new String[universe.length];

        for (int i = 0; i < this.names.length; i++) {
            String name;
            T value = universe[i];

            name = I18n.format(value instanceof NamedState namedState ? namedState.getKey() : value.name());

            this.names[i] = name;
        }
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public ControlElement<T> createElement(Dim2i dim, ControlElementFactory factory) {
        return factory.cyclingControlElement(this.option, dim, this.allowedValues, this.names);
    }

    @Override
    public int getMaxWidth() {
        return 70;
    }

}
