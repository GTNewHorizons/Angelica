package com.gtnewhorizons.angelica.compat.mojang;

public interface Element {
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    default boolean keyTyped(char typedChar, int keyCode) { return false; }

    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    default boolean changeFocus(boolean lookForwards) {
        return false;
    }
}
