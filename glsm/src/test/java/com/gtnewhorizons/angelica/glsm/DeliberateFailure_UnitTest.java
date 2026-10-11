package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Throwaway: exercises the CI test-result summary. Do not merge.
public class DeliberateFailure_UnitTest {

    @Test
    void failsOnPurpose() {
        System.out.println("stdout from the deliberately failing test");
        Assertions.assertEquals(4, 2 + 3, "deliberate failure for the CI summary test");
    }

    @Test
    void passes() {
        Assertions.assertEquals(4, 2 + 2);
    }
}
