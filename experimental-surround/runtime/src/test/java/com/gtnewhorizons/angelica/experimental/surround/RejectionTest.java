package com.gtnewhorizons.angelica.experimental.surround;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RejectionTest {

    static Stream<Named<RejectionCases.Case>> rejections() {
        return Arrays.stream(RejectionCases.CASES).map(c -> Named.of(c.name, c));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void anInvalidMixinIsRejectedAtLoad(RejectionCases.Case rejection) {
        rejection.assertRejected();
    }

    @Test
    void aRequireZeroCallSurroundThatMatchesNoCallIsNotRejected() {
        assertDoesNotThrow(() -> MixinTestBootstrap.transform(RejectionCases.PACKAGE + "." + RejectionCases.REQUIRE_ZERO_MISS.name));
    }

    @Test
    @Tag("mixinextras")
    void mixinExtrasSugarOnACallSiteEntryHandlerIsRejected() {
        RejectionCases.SUGAR.assertRejected();
    }
}
