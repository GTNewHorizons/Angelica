package com.gtnewhorizons.angelica.utils;

public enum MipmapStrategy {
    AUTO,
    MEAN,
    CUTOUT,
    STRICT_CUTOUT,
    DARK_CUTOUT;

    private static final MipmapStrategy[] VALUES = values();

    public static MipmapStrategy byName(String name) {
        if (name == null) {
            return null;
        }
        for (MipmapStrategy strategy : VALUES) {
            if (strategy.name().equalsIgnoreCase(name)) {
                return strategy;
            }
        }
        return null;
    }

    public int conflictPrecedence() {
        return switch (this) {
            case MEAN -> 4;
            case DARK_CUTOUT -> 3;
            case STRICT_CUTOUT -> 2;
            case CUTOUT -> 1;
            case AUTO -> 0;
        };
    }
}
