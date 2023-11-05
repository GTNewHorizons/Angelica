package com.gtnewhorizons.angelica.compat.mojang;

public enum LightType {
    SKY(15),
    BLOCK(0);

    public final int value;

    LightType(int value) {
        this.value = value;
    }
}
