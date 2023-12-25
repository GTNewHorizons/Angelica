package jss.notfine.gui.options.control;

import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.minecraft.client.resources.I18n;

public class NotFineControlValueFormatter {
    public static ControlValueFormatter multiplied(float multiplier) {
        return (value) -> String.valueOf((value * multiplier));
    }

    public static ControlValueFormatter powerOfTwo() {
        return (v) -> (v == 0) ? I18n.format("options.off") : I18n.format((int)Math.pow(2, v) + "x");
    }

}
