package com.gtnewhorizons.angelica.api.debug;

public interface DebugFact {

    DebugFact text(CharSequence text);

    DebugFact text(char c);

    DebugFact value(CharSequence value);

    DebugFact value(int value);

    DebugFact value(long value);

    DebugFact value(double value, int decimals);

    DebugFact padded(long value, int width);
}
