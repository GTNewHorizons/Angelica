package me.jellysquid.mods.sodium.client.gui.options.control;

import net.minecraft.client.resources.I18n;

public interface ControlValueFormatter {
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? I18n.format("options.guiScale.auto") : I18n.format(v + "x");
    }

    static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? I18n.format("options.framerateLimit.max") : I18n.format("options.framerate", v);
    }

    static ControlValueFormatter brightness() {
        return (v) -> {
            if (v == 0) {
                return I18n.format("options.gamma.min");
            } else if (v == 100) {
                return I18n.format("options.gamma.max");
            } else {
                return v + "%";
            }
        };
    }

    String format(int value);

    static ControlValueFormatter percentage() {
        return (v) -> v + "%";
    }

    static ControlValueFormatter multiplier() {
        return (v) -> v + "x";
    }

    static ControlValueFormatter quantity(String name) {
        return (v) -> I18n.format(name, v);
    }

    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        return (v) -> I18n.format(v == 0 ? disableText : name, v);
    }

    static ControlValueFormatter number() {
        return String::valueOf;
    }
}
