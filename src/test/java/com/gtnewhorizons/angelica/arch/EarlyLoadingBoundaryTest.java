package com.gtnewhorizons.angelica.arch;

import com.gtnewhorizons.angelica.arch.ArchScan.Access;
import com.gtnewhorizons.angelica.arch.ArchScan.Dir;
import com.gtnewhorizons.angelica.arch.ArchScan.ScannedClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyLoadingBoundaryTest {

    private static final String TRACY = "com.gtnewhorizons.angelica.glsm.profiling.Tracy";

    private static final Pattern GUARDED = Pattern.compile(
        "com\\.gtnewhorizons\\.angelica\\.(loading\\.[^.]+|mixins\\.Mixins(\\$[^.]*)?|AngelicaMod(\\$[^.]*)?|config\\.AngelicaConfig(\\$[^.]*)?)");

    @Test
    void earlyLoadingClassesDoNotAccessTracy() {
        int guarded = 0;
        final List<String> violations = new ArrayList<>();
        for (ScannedClass origin : ArchScan.in(Dir.MAIN)) {
            if (!GUARDED.matcher(origin.name()).matches()) continue;
            guarded++;
            for (Access access : origin.accesses()) {
                if (access.owner().equals(TRACY)) violations.add(origin.name() + "." + access.method() + " -> " + access.target());
            }
        }
        assertFalse(guarded == 0, "no guarded classes scanned");
        assertTrue(violations.isEmpty(), "Coremod-phase code must not touch glsm.profiling.Tracy:\n" + String.join("\n", violations));
    }
}
