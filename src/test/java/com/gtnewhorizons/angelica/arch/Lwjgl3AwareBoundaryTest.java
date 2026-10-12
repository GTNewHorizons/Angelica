package com.gtnewhorizons.angelica.arch;

import com.gtnewhorizons.angelica.arch.ArchScan.Access;
import com.gtnewhorizons.angelica.arch.ArchScan.Dir;
import com.gtnewhorizons.angelica.arch.ArchScan.Member;
import com.gtnewhorizons.angelica.arch.ArchScan.ScannedClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the lwjgl3ify remap boundary.
 */
class Lwjgl3AwareBoundaryTest {

    private static final String AWARE = "me.eigenraven.lwjgl3ify.api.Lwjgl3Aware";

    /** Packages whose classes lwjgl3ify never transforms, so their descriptors match on both sides. */
    private static final List<String> UNTRANSFORMED_PACKAGES = List.of("org.lwjgl.", "org.lwjglx.");

    /** Modules built against native LWJGL3; every class in them must be aware. */
    private static final List<String> AWARE_MODULES = List.of(
        "com.gtnewhorizons.angelica.sdlgpu.",
        "com.gtnewhorizons.angelica.lwjgl3.");

    private static final List<ScannedClass> classes = ArchScan.in(Dir.values());

    @Test
    void awareClassesDoNotReadRemappedMembers() {
        final List<String> violations = new ArrayList<>();
        for (ScannedClass origin : classes) {
            if (!isAware(origin.name())) continue;
            for (Access access : origin.accesses()) {
                if (isUntransformed(access.owner()) || isAware(access.owner())) continue;
                if (mentionsLwjgl3Type(access)) violations.add(describe(origin, access));
            }
        }
        assertTrue(violations.isEmpty(), "@Lwjgl3Aware code referencing a remapped member with an org.lwjgl type in its descriptor:\n" + String.join("\n", violations));
    }

    @Test
    void remappedClassesDoNotReadAwareMembers() {
        final List<String> violations = new ArrayList<>();
        for (ScannedClass origin : classes) {
            if (isAware(origin.name()) || isUntransformed(origin.name())) continue;
            for (Access access : origin.accesses()) {
                if (!isAware(access.owner())) continue;
                if (mentionsLwjgl3Type(access)) violations.add(describe(origin, access));
            }
        }
        assertTrue(violations.isEmpty(), "Remapped code referencing an @Lwjgl3Aware member with an org.lwjgl type in its descriptor:\n" + String.join("\n", violations));
    }

    /** Verifies the build-time injection in :sdl-gpu / :lwjgl3-backend actually ran over the class output. */
    @Test
    void nativeLwjgl3ModulesAreFullyAware() {
        final List<String> missing = new ArrayList<>();
        int inAwareModules = 0;
        for (ScannedClass c : classes) {
            if (AWARE_MODULES.stream().noneMatch(p -> c.name().startsWith(p))) continue;
            inAwareModules++;
            if (!isAware(c.name())) missing.add(c.name());
        }
        assertFalse(inAwareModules == 0, "no classes scanned under " + AWARE_MODULES);
        assertTrue(missing.isEmpty(), "Classes missing @Lwjgl3Aware:\n" + String.join("\n", missing));
    }

    @Test
    void renderBackendSignaturesAreRemapSafe() {
        final ScannedClass renderBackend = ArchScan.get("com.gtnewhorizons.angelica.glsm.backend.RenderBackend");
        final List<String> violations = new ArrayList<>();
        for (Member method : renderBackend.methods()) {
            if (method.name().startsWith("<")) continue;
            if (ArchScan.signatureTypes(method.desc()).stream().anyMatch(Lwjgl3AwareBoundaryTest::isRemapUnsafeType)) {
                violations.add(renderBackend.name() + "." + method.name() + method.desc());
            }
        }
        assertTrue(violations.isEmpty(), "RenderBackend methods with an org.lwjgl/org.lwjglx/lwjgl3ify type in their signature:\n" + String.join("\n", violations));
    }

    private static boolean isAware(String name) {
        final ScannedClass c = ArchScan.find(name);
        return c != null && c.annotations().contains(AWARE);
    }

    private static boolean isUntransformed(String name) {
        return UNTRANSFORMED_PACKAGES.stream().anyMatch(name::startsWith);
    }

    private static boolean isLwjgl3Type(String name) {
        return name.startsWith("org.lwjgl.");
    }

    private static boolean isRemapUnsafeType(String name) {
        return isLwjgl3Type(name) || name.startsWith("org.lwjglx.") || name.startsWith("me.eigenraven.lwjgl3ify.");
    }

    private static boolean mentionsLwjgl3Type(Access access) {
        return access.descriptorTypes().stream().anyMatch(Lwjgl3AwareBoundaryTest::isLwjgl3Type);
    }

    private static String describe(ScannedClass origin, Access access) {
        return "  " + origin.name() + "." + access.method() + " -> " + access.target();
    }
}
