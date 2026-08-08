package com.gtnewhorizons.angelica.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the :sdl-gpu module boundary
 */
class SDLGPUEncapsulationTest {

    private static final String SDLGPU_ROOT = "com.gtnewhorizons.angelica.sdlgpu";

    private static final Set<String> PUBLIC_ENTRIES = Set.of(
        SDLGPU_ROOT + ".SDLGPURenderBackend",         // ServiceLoader
        SDLGPU_ROOT + ".SDLGPULWJGLService",          // ServiceLoader
        SDLGPU_ROOT + ".SDLGPUGate",                  // Mixins, MixinInitGLStateManager
        SDLGPU_ROOT + ".SDLGPUDisplayBridge",         // MixinForgeHooksClient_SDLGPUDisplay
        SDLGPU_ROOT + ".device.SDLDrawable",          // MixinForgeHooksClient_SDLGPUDisplay
        SDLGPU_ROOT + ".shader.ShaderManager"         // Iris, IrisGLSMBridge
    );

    private static final DescribedPredicate<JavaClass> SDLGPU_INTERNAL =
        new DescribedPredicate<>("sdlgpu internals (anything under " + SDLGPU_ROOT + " not on the allow-list)") {
            @Override
            public boolean test(JavaClass c) {
                final String name = c.getName();
                return name.startsWith(SDLGPU_ROOT + ".") && !PUBLIC_ENTRIES.contains(name);
            }
        };

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPaths("build/classes/java/main", "build/classes/java/mixin");

    private static final ArchRule NO_SDLGPU_INTERNALS_FROM_OUTSIDE = noClasses()
        .that().resideOutsideOfPackage(SDLGPU_ROOT + "..")
        .should().dependOnClassesThat(SDLGPU_INTERNAL)
        .because("only the allow-listed classes may be referenced by name from outside :sdl-gpu");

    @Test
    void noSdlGpuInternalsFromOutside() {
        NO_SDLGPU_INTERNALS_FROM_OUTSIDE.check(CLASSES);
    }
}
