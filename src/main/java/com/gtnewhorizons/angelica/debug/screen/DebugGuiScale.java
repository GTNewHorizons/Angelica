package com.gtnewhorizons.angelica.debug.screen;

public final class DebugGuiScale {

    public static final int UNCHANGED = -1;
    public static final int AUTO = 0;

    private DebugGuiScale() {}

    public static int maxScale(int displayWidth, int displayHeight, boolean evenForUnicode) {
        int scale = 1;
        while (displayWidth / (scale + 1) >= 320 && displayHeight / (scale + 1) >= 240) {
            scale++;
        }
        if (evenForUnicode && scale % 2 != 0 && scale != 1) scale--;
        return scale;
    }

    public static int offeredMax(int maxScale, int gameScale) {
        return Math.max(1, Math.min(maxScale, gameScale));
    }

    public static int resolve(int setting, int gameScale, int displayWidth, int displayHeight, boolean evenForUnicode) {
        if (setting == UNCHANGED) return gameScale;
        final int max = maxScale(displayWidth, displayHeight, evenForUnicode);
        final int wanted = setting == AUTO ? Math.max(1, max / 2) : Math.min(setting, max);
        return Math.max(1, Math.min(wanted, gameScale));
    }
}
