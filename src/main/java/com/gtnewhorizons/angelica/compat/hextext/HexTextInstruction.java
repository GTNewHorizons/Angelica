package com.gtnewhorizons.angelica.compat.hextext;

public final class HexTextInstruction {

    public enum Type {
        APPLY_RGB,
        PUSH_RGB,
        POP_COLOR,
        RESET_TO_BASE,
        APPLY_VANILLA_COLOR,
        SET_RANDOM,
        SET_BOLD,
        SET_STRIKETHROUGH,
        SET_UNDERLINE,
        SET_ITALIC,
        SET_RAINBOW,
        SET_DINNERBONE,
        SET_IGNITE,
        SET_SHAKE
    }

    private final Type type;
    private final int rgb;
    private final boolean clearStack;
    private final int parameter;
    private final boolean enabled;
    private final boolean resetFormatting;

    public HexTextInstruction(Type type, int rgb, boolean clearStack, int parameter, boolean enabled,
                              boolean resetFormatting) {
        this.type = type;
        this.rgb = rgb;
        this.clearStack = clearStack;
        this.parameter = parameter;
        this.enabled = enabled;
        this.resetFormatting = resetFormatting;
    }

    public Type type() {
        return type;
    }

    public int rgb() {
        return rgb;
    }

    public boolean clearStack() {
        return clearStack;
    }

    public int parameter() {
        return parameter;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean resetFormatting() {
        return resetFormatting;
    }
}
