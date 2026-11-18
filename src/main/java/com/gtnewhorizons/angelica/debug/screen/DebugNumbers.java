package com.gtnewhorizons.angelica.debug.screen;

public final class DebugNumbers {

    private static final long[] POW10 = { 1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L };
    private static final double MAX_SCALED = 9.0e18;

    private DebugNumbers() {}

    public static void appendFixed(StringBuilder out, double value, int decimals) {
        final int places = Math.max(0, Math.min(decimals, POW10.length - 1));
        final long scale = POW10[places];
        final double magnitude = Math.abs(value) * scale;
        if (Double.isNaN(value) || Double.isInfinite(value) || magnitude >= MAX_SCALED) {
            out.append(value);
            return;
        }
        final long scaled = Math.round(magnitude);
        if (value < 0 && scaled != 0) out.append('-');
        out.append(scaled / scale);
        if (places == 0) return;
        out.append('.');
        appendPadded(out, scaled % scale, places);
    }

    public static void appendPadded(StringBuilder out, long value, int width) {
        if (value >= 0) {
            int digits = 1;
            for (long rest = value / 10; rest > 0; rest /= 10) {
                digits++;
            }
            for (int i = digits; i < width; i++) {
                out.append('0');
            }
        }
        out.append(value);
    }
}
