package net.coderbot.iris.gl.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardMacrosVersionTest {
    @Test
    void releaseAndPrereleaseTags() {
        assertEquals("20228000", StandardMacros.formatAngelicaVersion("2.2.28"));
        assertEquals("20000008", StandardMacros.formatAngelicaVersion("2.0.0-alpha8"));
        assertEquals("10000078", StandardMacros.formatAngelicaVersion("1.0.0-beta78-pre"));
        assertEquals("20228000", StandardMacros.formatAngelicaVersion("2.2.28-pre"));
    }

    @Test
    void devBuildsIgnoreBranchAndHashDigits() {
        assertEquals("20228000", StandardMacros.formatAngelicaVersion("2.2.28-master.7+12857006ab-dirty"));
        assertEquals("20228000", StandardMacros.formatAngelicaVersion("2.2.28-fix2117.3+12857006ab"));
        assertEquals("20000012", StandardMacros.formatAngelicaVersion("2.0.0-alpha12-master.3+12857006ab"));
    }

    @Test
    void unparseableVersionsReportNewest() {
        assertEquals("99999999", StandardMacros.formatAngelicaVersion("moltenvk-v3.4.2-master.1+38af64f10c-dirty"));
        assertEquals("99999999", StandardMacros.formatAngelicaVersion("NO-GIT-TAG-SET"));
    }
}
