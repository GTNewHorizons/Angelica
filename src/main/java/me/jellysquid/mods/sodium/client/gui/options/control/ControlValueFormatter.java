package me.jellysquid.mods.sodium.client.gui.options.control;

import net.minecraft.text.TranslatableText;

public interface ControlValueFormatter {
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? new TranslatableText("options.guiScale.auto").getString() : new TranslatableText(v + "x").getString();
    }

    static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? new TranslatableText("options.framerateLimit.max").getString() : new TranslatableText("options.framerate", v).getString();
    }

    static ControlValueFormatter brightness() {
        return (v) -> {
            if (v == 0) {
                return new TranslatableText("options.gamma.min").getString();
            } else if (v == 100) {
                return new TranslatableText("options.gamma.max").getString();
            } else {
                return new TranslatableText(v + "%").getString();
            }
        };
    }

    String format(int value);

    static ControlValueFormatter percentage() {
        return (v) -> new TranslatableText(v + "%").getString();
    }

    static ControlValueFormatter multiplier() {
        return (v) -> new TranslatableText(v + "x").getString();
    }

    static ControlValueFormatter quantity(String name) {
        return (v) -> new TranslatableText(name, v).getString();
    }

    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        return (v) -> new TranslatableText(v == 0 ? disableText : name, v).getString();
    }

    static ControlValueFormatter number() {
        return String::valueOf;
    }
}
