package com.gtnewhorizons.angelica.arch;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyLoadingBoundaryTest {

    private static final String TRACY = "com.gtnewhorizons.angelica.glsm.profiling.Tracy";

    private static final Pattern GUARDED = Pattern.compile(
        ".*/com/gtnewhorizons/angelica/(loading/[^/]+|mixins/Mixins(\\$[^/]*)?|AngelicaMod(\\$[^/]*)?|config/AngelicaConfig(\\$[^/]*)?)\\.class$");

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(location -> location.matches(GUARDED))
        .importPaths("build/classes/java/main");

    @Test
    void earlyLoadingClassesDoNotAccessTracy() {
        assertFalse(classes.isEmpty(), "no guarded classes imported");
        final List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            for (JavaAccess<?> access : origin.getAccessesFromSelf()) {
                if (access.getTargetOwner().getName().equals(TRACY)) {
                    violations.add(origin.getFullName() + " -> " + access.getTarget().getFullName());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Coremod-phase code must not touch glsm.profiling.Tracy:\n" + String.join("\n", violations));
    }
}
