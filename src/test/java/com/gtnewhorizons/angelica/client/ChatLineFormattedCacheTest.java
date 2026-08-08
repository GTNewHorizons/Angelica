package com.gtnewhorizons.angelica.client;

import com.gtnewhorizons.angelica.mixins.interfaces.ChatLineFormattedAccessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatLineFormattedCacheTest {

    private static final class Line implements ChatLineFormattedAccessor {
        private String formatted;
        private long formattedEpoch = Long.MIN_VALUE;

        @Override
        public String angelica$getFormatted(long epoch) {
            return formattedEpoch == epoch ? formatted : null;
        }

        @Override
        public void angelica$setFormatted(String formatted, long epoch) {
            this.formatted = formatted;
            this.formattedEpoch = epoch;
        }
    }

    @Test
    void aFreshLineHasNothingCached() {
        assertNull(new Line().angelica$getFormatted(0L));
    }

    @Test
    void theSameEpochReturnsTheCachedInstance() {
        final Line line = new Line();
        final String formatted = "§ahello";
        line.angelica$setFormatted(formatted, 1234L);
        assertSame(formatted, line.angelica$getFormatted(1234L));
    }

    @Test
    void aTranslationEpochChangeInvalidates() {
        final Line line = new Line();
        line.angelica$setFormatted("§aold locale", 1234L);
        assertNull(line.angelica$getFormatted(5678L), "a language reload must force a rebuild");

        line.angelica$setFormatted("§anew locale", 5678L);
        assertEquals("§anew locale", line.angelica$getFormatted(5678L));
        assertNull(line.angelica$getFormatted(1234L), "the stale epoch never comes back");
    }

    @Test
    void obfuscatedCodesAreCachedVerbatim() {
        final Line line = new Line();
        final String formatted = "§kMAGIC§r plain";
        line.angelica$setFormatted(formatted, 7L);
        assertEquals(formatted, line.angelica$getFormatted(7L));
    }

    @Test
    void zeroIsAUsableEpoch() {
        final Line line = new Line();
        line.angelica$setFormatted("x", 0L);
        assertEquals("x", line.angelica$getFormatted(0L));
    }
}
