package com.gtnewhorizons.angelica.glsm.hooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSMInitConfigDefaultsTest {

    @Test
    void emptyBuilderLeavesDsaToTheProbe() {
        assertTrue(GLSMInitConfig.builder().build().isDSAEnabled());
        assertFalse(GLSMInitConfig.builder().enableDSA(false).build().isDSAEnabled());
    }
}
