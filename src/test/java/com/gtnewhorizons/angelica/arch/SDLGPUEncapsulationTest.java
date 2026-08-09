package com.gtnewhorizons.angelica.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.ReferencedClassObject;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the :sdl-gpu module boundary
 */
class SDLGPUEncapsulationTest {

    private static final String SDLGPU_ROOT = "com.gtnewhorizons.angelica.sdlgpu";

    private static final String GATE = SDLGPU_ROOT + ".SDLGPUGate";

    private static final String LWJGL3_AWARE = "me.eigenraven.lwjgl3ify.api.Lwjgl3Aware";

    private static final DescribedPredicate<JavaClass> SDLGPU_BEHIND_THE_GATE =
        new DescribedPredicate<>("anything under " + SDLGPU_ROOT + " other than SDLGPUGate") {
            @Override
            public boolean test(JavaClass c) {
                final String name = c.getName();
                return name.startsWith(SDLGPU_ROOT + ".") && !GATE.equals(name);
            }
        };

    private static final DescribedPredicate<JavaClass> LWJGL3_ONLY_TYPE =
        new DescribedPredicate<>("types that only exist under lwjgl3ify/LWJGL3") {
            @Override
            public boolean test(JavaClass c) {
                final String name = c.getName();
                if (LWJGL3_AWARE.equals(name)) return false;
                return name.startsWith("org.lwjglx.") || name.startsWith("me.eigenraven.lwjgl3ify.") || name.startsWith("org.lwjgl.sdl.");
            }
        };

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPaths("build/classes/java/main", "build/classes/java/mixin", "glsm/build/classes/java/main");

    private static final JavaClasses SDLGPU_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPaths("sdl-gpu/build/classes/java/main");

    private static final ArchRule SDLGPU_REACHED_ONLY_THROUGH_THE_GATE = noClasses()
        .that().resideOutsideOfPackage(SDLGPU_ROOT + "..")
        .should().dependOnClassesThat(SDLGPU_BEHIND_THE_GATE)
        .because("SDLGPUGate is the only :sdl-gpu class safe to load without LWJGL3");

    private static final ArchRule NO_LWJGL3_ONLY_TYPES = noClasses()
        .that().resideOutsideOfPackage(SDLGPU_ROOT + "..")
        .should().dependOnClassesThat(LWJGL3_ONLY_TYPE)
        .because("main, mixin and glsm all load on an LWJGL2 launch, where these types are absent");

    @Test
    void sdlGpuIsReachedOnlyThroughTheGate() {
        SDLGPU_REACHED_ONLY_THROUGH_THE_GATE.check(CLASSES);
    }

    @Test
    void noLwjgl3OnlyTypesOutsideSdlGpu() {
        NO_LWJGL3_ONLY_TYPES.check(CLASSES);
    }

    @Test
    void gateStaticInitIsLwjgl3Free() {
        final Optional<JavaStaticInitializer> clinit = SDLGPU_CLASSES.get(GATE).getStaticInitializer();
        assertTrue(clinit.isPresent(), GATE + " has no static initializer to check; the importer path is likely wrong");

        final List<String> violations = new ArrayList<>();
        for (JavaAccess<?> access : clinit.get().getAccessesFromSelf()) {
            if (isLwjgl3(access.getTargetOwner())) violations.add("  " + access.getTarget().getFullName());
        }
        for (ReferencedClassObject ref : clinit.get().getReferencedClassObjects()) {
            if (isLwjgl3(ref.getValue())) violations.add("  " + ref.getValue().getName() + ".class");
        }
        assertTrue(violations.isEmpty(), GATE + " static init resolves LWJGL3 types:\n" + String.join("\n", violations));
    }

    private static boolean isLwjgl3(JavaClass c) {
        return LWJGL3_ONLY_TYPE.test(c) || c.getName().startsWith("org.lwjgl.");
    }
}
