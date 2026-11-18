package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugFact;

public final class DebugFactImpl implements DebugFact {

    private static final String VALUE_ON = "\u00a7f";
    private static final String VALUE_OFF = "\u00a7r";

    private static final String PLAIN = "";

    private StringBuilder out;
    private String valueOn = VALUE_ON;
    private String valueOff = VALUE_OFF;

    public DebugFactImpl into(StringBuilder out) {
        this.out = out;
        valueOn = VALUE_ON;
        valueOff = VALUE_OFF;
        return this;
    }

    public DebugFactImpl intoPlain(StringBuilder out) {
        this.out = out;
        valueOn = PLAIN;
        valueOff = PLAIN;
        return this;
    }

    @Override
    public DebugFact text(CharSequence text) {
        out.append(text);
        return this;
    }

    @Override
    public DebugFact text(char c) {
        out.append(c);
        return this;
    }

    @Override
    public DebugFact value(CharSequence value) {
        out.append(valueOn).append(value).append(valueOff);
        return this;
    }

    @Override
    public DebugFact value(int value) {
        out.append(valueOn).append(value).append(valueOff);
        return this;
    }

    @Override
    public DebugFact value(long value) {
        out.append(valueOn).append(value).append(valueOff);
        return this;
    }

    @Override
    public DebugFact value(double value, int decimals) {
        out.append(valueOn);
        DebugNumbers.appendFixed(out, value, decimals);
        out.append(valueOff);
        return this;
    }

    @Override
    public DebugFact padded(long value, int width) {
        out.append(valueOn);
        DebugNumbers.appendPadded(out, value, width);
        out.append(valueOff);
        return this;
    }
}
