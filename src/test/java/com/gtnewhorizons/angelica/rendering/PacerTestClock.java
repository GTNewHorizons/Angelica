package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Fake clock plus a parker that advances it; with the defaults a park takes exactly the requested time. */
final class PacerTestClock {

    static final long MS = 1_000_000L;
    static final long P60 = 1_000_000_000L / 60;
    static final long P5994 = 1_000_000_000L * 1001 / 60_000;
    static final long P144 = 1_000_000_000L / 144;

    long now;
    long lag;
    long granularity;
    /** One-shot override of the next park's real duration; negative means unset. */
    long nextParkActual = -1;
    int parks;
    long lastRequest;

    LongSupplier clock() {
        return () -> now;
    }

    LongConsumer parker() {
        return nanos -> {
            parks++;
            lastRequest = nanos;
            final long actual;
            if (nextParkActual >= 0) {
                actual = nextParkActual;
                nextParkActual = -1;
            } else {
                actual = Math.max(nanos + lag, granularity);
            }
            now += actual;
        };
    }

    static boolean driverPaced(PacerCore core) {
        return Reflect.<Boolean>invoke(core, "driverPaced", new Class<?>[0]);
    }

    static long cadenceNanos(PacerCore core) {
        return Reflect.<Long>get(core, "cadenceNanos");
    }

    static long cpuHighNanos(PacerCore core) {
        return Reflect.<Long>get(core, "cpuHighNanos");
    }
}
