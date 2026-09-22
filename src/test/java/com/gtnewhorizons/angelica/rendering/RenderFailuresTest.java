package com.gtnewhorizons.angelica.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderFailuresTest {
    @Test
    void preservesIdentityAndOrderedSuppression() {
        final Exception primary = new Exception();
        final Exception first = new Exception();
        final Error second = new Error();
        assertSame(primary, RenderFailures.suppress(primary, first));
        assertSame(primary, RenderFailures.suppress(primary, second));
        assertSame(primary, RenderFailures.suppress(primary, primary));
        assertArrayEquals(new Throwable[] { first, second }, primary.getSuppressed());
        assertSame(primary, assertThrows(Exception.class, () -> RenderFailures.rethrow(primary)));
    }

    @Test
    void cleanupOnlyAndRepeatedFailures() {
        final Error cleanup = new Error();
        final Throwable failure = RenderFailures.suppress(null, cleanup);
        assertSame(cleanup, RenderFailures.suppress(failure, cleanup));
        assertSame(cleanup, assertThrows(Error.class, () -> RenderFailures.rethrow(failure)));
        assertArrayEquals(new Throwable[0], cleanup.getSuppressed());
        RenderFailures.rethrow(null);
        RenderFailures.rethrowWrapped(null);
    }

    @Test
    void particleWrappingPreservesUncheckedIdentity() {
        final Exception checked = new Exception();
        final RuntimeException runtime = new RuntimeException();
        final Error error = new Error();
        assertSame(checked, assertThrows(RuntimeException.class, () -> RenderFailures.rethrowWrapped(checked)).getCause());
        assertSame(runtime, assertThrows(RuntimeException.class, () -> RenderFailures.rethrowWrapped(runtime)));
        assertSame(error, assertThrows(Error.class, () -> RenderFailures.rethrowWrapped(error)));
    }
}
