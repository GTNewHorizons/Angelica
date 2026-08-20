package com.gtnewhorizons.angelica.arch;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPaths(
            "build/classes/java/main",
            "build/classes/java/mixin",
            "glsm/build/classes/java/main",
            "lwjgl3-backend/build/classes/java/main",
            "sdl-gpu/build/classes/java/main");

    @Test
    void awareClassesDoNotReadRemappedMembers() {
        final List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            if (!isAware(origin)) continue;
            for (JavaAccess<?> access : origin.getAccessesFromSelf()) {
                final JavaClass owner = access.getTargetOwner();
                if (isUntransformed(owner) || isAware(owner)) continue;
                if (mentionsLwjgl3Type(access.getTarget())) violations.add(describe(access));
            }
        }
        assertTrue(violations.isEmpty(), "@Lwjgl3Aware code referencing a remapped member with an org.lwjgl type in its descriptor:\n" + String.join("\n", violations));
    }

    @Test
    void remappedClassesDoNotReadAwareMembers() {
        final List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            if (isAware(origin) || isUntransformed(origin)) continue;
            for (JavaAccess<?> access : origin.getAccessesFromSelf()) {
                if (!isAware(access.getTargetOwner())) continue;
                if (mentionsLwjgl3Type(access.getTarget())) violations.add(describe(access));
            }
        }
        assertTrue(violations.isEmpty(), "Remapped code referencing an @Lwjgl3Aware member with an org.lwjgl type in its descriptor:\n" + String.join("\n", violations));
    }

    /** Verifies the build-time injection in :sdl-gpu / :lwjgl3-backend actually ran over the class output. */
    @Test
    void nativeLwjgl3ModulesAreFullyAware() {
        final List<String> missing = new ArrayList<>();
        for (JavaClass c : classes) {
            if (AWARE_MODULES.stream().anyMatch(p -> c.getName().startsWith(p)) && !isAware(c)) {
                missing.add(c.getName());
            }
        }
        assertTrue(missing.isEmpty(), "Classes missing @Lwjgl3Aware:\n" + String.join("\n", missing));
    }

    @Test
    void renderBackendSignaturesAreRemapSafe() {
        final JavaClass renderBackend = classes.get("com.gtnewhorizons.angelica.glsm.backend.RenderBackend");
        final List<String> violations = new ArrayList<>();
        for (JavaMethod method : renderBackend.getMethods()) {
            final List<JavaClass> types = new ArrayList<>(method.getRawParameterTypes());
            types.add(method.getRawReturnType());
            if (types.stream().anyMatch(Lwjgl3AwareBoundaryTest::isRemapUnsafeType)) {
                violations.add(method.getFullName());
            }
        }
        assertTrue(violations.isEmpty(), "RenderBackend methods with an org.lwjgl/org.lwjglx/lwjgl3ify type in their signature:\n" + String.join("\n", violations));
    }

    private static boolean isAware(JavaClass c) {
        return c.isAnnotatedWith(AWARE);
    }

    private static boolean isUntransformed(JavaClass c) {
        return UNTRANSFORMED_PACKAGES.stream().anyMatch(p -> c.getName().startsWith(p));
    }

    private static boolean isLwjgl3Type(JavaClass type) {
        return type.getName().startsWith("org.lwjgl.");
    }

    private static boolean isRemapUnsafeType(JavaClass type) {
        final String name = type.getName();
        return isLwjgl3Type(type) || name.startsWith("org.lwjglx.") || name.startsWith("me.eigenraven.lwjgl3ify.");
    }

    private static boolean mentionsLwjgl3Type(AccessTarget target) {
        return descriptorTypes(target).stream().anyMatch(Lwjgl3AwareBoundaryTest::isLwjgl3Type);
    }

    private static List<JavaClass> descriptorTypes(AccessTarget target) {
        if (target instanceof AccessTarget.FieldAccessTarget field) {
            return List.of(field.getRawType());
        }
        if (target instanceof AccessTarget.CodeUnitCallTarget call) {
            final List<JavaClass> types = new ArrayList<>(call.getRawParameterTypes());
            if (target instanceof AccessTarget.MethodCallTarget method) {
                types.add(method.getRawReturnType());
            }
            return types;
        }
        return List.of();
    }

    private static String describe(JavaAccess<?> access) {
        return "  " + access.getOwner().getFullName() + " -> " + access.getTarget().getFullName();
    }
}
