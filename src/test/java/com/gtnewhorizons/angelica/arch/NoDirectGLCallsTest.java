package com.gtnewhorizons.angelica.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that all GL calls go through GLStateManager / RENDER_BACKEND. Direct method calls on org.lwjgl.opengl GL command classes are banned outside
 * of the GL backend implementation and the bytecode redirector.
 *
 * Field access (GL constants like GL11.GL_BLEND) is not affected
 *
 * LWJGL infrastructure classes (Display, ContextAttribs, PixelFormat, Drawable) are excluded
 */
class NoDirectGLCallsTest {

    // Matches GL command classes but not LWJGL infrastructure (Display, etc.)
    // Covers: GL11-GL44, ARB*, EXT*, NV*, AMD*, ATI*, KHR*, INTEL*, GLContext, plus OpenGlHelper
    private static final String GL_COMMAND_CLASSES = "org\\.lwjgl\\.opengl\\.(?!Display|ContextAttribs|PixelFormat|Drawable|SharedDrawable).*";

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPaths(
            "build/classes/java/main",
            "build/classes/java/mixin"
        );

    private static final ArchRule NO_DIRECT_GL_CALLS = noClasses()
        .that().resideOutsideOfPackages(
            "com.gtnewhorizons.angelica.glsm.backend..",
            "com.gtnewhorizons.angelica.loading.."
        )
        .should().callMethodWhere(
            target(owner(nameMatching(GL_COMMAND_CLASSES)))
        )
        .because("GL calls must go through GLStateManager/RENDER_BACKEND, not directly to LWJGL");

    @Test
    void noDirectGLCalls() {
        NO_DIRECT_GL_CALLS.check(classes);
    }
}
