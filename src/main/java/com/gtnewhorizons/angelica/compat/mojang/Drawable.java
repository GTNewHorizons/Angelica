package com.gtnewhorizons.angelica.compat.mojang;

public interface Drawable {
    default void render(int mouseX, int mouseY, float delta) {}
}
