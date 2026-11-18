package com.gtnewhorizons.angelica.arch;

import com.gtnewhorizons.angelica.arch.ArchScan.Access;
import com.gtnewhorizons.angelica.arch.ArchScan.Dir;
import com.gtnewhorizons.angelica.arch.ArchScan.Kind;
import com.gtnewhorizons.angelica.arch.ArchScan.ScannedClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final Pattern GL_COMMAND_CLASSES = Pattern.compile("org\\.lwjgl\\.opengl\\.(?!Display|ContextAttribs|PixelFormat|Drawable|SharedDrawable).*");

    private static final List<String> EXEMPT_PACKAGES = List.of(
        "com.gtnewhorizons.angelica.glsm.backend.",
        "com.gtnewhorizons.angelica.loading.");

    @Test
    void noDirectGLCalls() {
        final List<String> violations = new ArrayList<>();
        for (ScannedClass origin : ArchScan.in(Dir.MAIN, Dir.MIXIN)) {
            if (EXEMPT_PACKAGES.stream().anyMatch(origin.name()::startsWith)) continue;
            for (Access access : origin.accesses()) {
                if (access.kind() != Kind.CALL || access.name().equals("<init>")) continue;
                if (GL_COMMAND_CLASSES.matcher(access.owner()).matches()) violations.add("  " + origin.name() + "." + access.method() + " -> " + access.target());
            }
        }
        assertTrue(violations.isEmpty(), "GL calls must go through GLStateManager, not directly to LWJGL or the RENDER_BACKEND:\n" + String.join("\n", violations));
    }
}
