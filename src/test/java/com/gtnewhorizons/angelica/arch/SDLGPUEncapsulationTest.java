package com.gtnewhorizons.angelica.arch;

import com.gtnewhorizons.angelica.arch.ArchScan.Access;
import com.gtnewhorizons.angelica.arch.ArchScan.ClassConstant;
import com.gtnewhorizons.angelica.arch.ArchScan.Dir;
import com.gtnewhorizons.angelica.arch.ArchScan.ScannedClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the :sdl-gpu module boundary
 */
class SDLGPUEncapsulationTest {

    private static final String SDLGPU_ROOT = "com.gtnewhorizons.angelica.sdlgpu";

    private static final String GATE = SDLGPU_ROOT + ".SDLGPUGate";

    private static final String LWJGL3_AWARE = "me.eigenraven.lwjgl3ify.api.Lwjgl3Aware";

    @Test
    void sdlGpuIsReachedOnlyThroughTheGate() {
        final List<String> violations = referencesOutsideSdlGpu(SDLGPUEncapsulationTest::isBehindTheGate);
        assertTrue(violations.isEmpty(), "SDLGPUGate is the only :sdl-gpu class safe to load without LWJGL3:\n" + String.join("\n", violations));
    }

    @Test
    void noLwjgl3OnlyTypesOutsideSdlGpu() {
        final List<String> violations = referencesOutsideSdlGpu(SDLGPUEncapsulationTest::isLwjgl3OnlyType);
        assertTrue(violations.isEmpty(), "main, mixin and glsm all load on an LWJGL2 launch, where these types are absent:\n" + String.join("\n", violations));
    }

    @Test
    void gateStaticInitIsLwjgl3Free() {
        final ScannedClass gate = ArchScan.get(GATE);
        assertTrue(gate.methods().stream().anyMatch(m -> m.name().equals("<clinit>")), GATE + " has no static initializer to check; the scan path is likely wrong");

        final List<String> violations = new ArrayList<>();
        for (Access access : gate.accesses()) {
            if (isStaticInit(access.method()) && isLwjgl3(access.owner())) violations.add("  " + access.target());
        }
        for (ClassConstant constant : gate.classConstants()) {
            if (isStaticInit(constant.method()) && isLwjgl3(constant.type())) violations.add("  " + constant.type() + ".class");
        }
        assertTrue(violations.isEmpty(), GATE + " static init resolves LWJGL3 types:\n" + String.join("\n", violations));
    }

    private static List<String> referencesOutsideSdlGpu(Predicate<String> forbidden) {
        final List<String> violations = new ArrayList<>();
        for (ScannedClass origin : ArchScan.in(Dir.MAIN, Dir.MIXIN, Dir.GLSM)) {
            if (origin.name().startsWith(SDLGPU_ROOT + ".")) continue;
            for (String reference : origin.references()) {
                if (forbidden.test(reference)) violations.add("  " + origin.name() + " -> " + reference);
            }
        }
        Collections.sort(violations);
        return violations;
    }

    private static boolean isBehindTheGate(String name) {
        return name.startsWith(SDLGPU_ROOT + ".") && !GATE.equals(name);
    }

    private static boolean isLwjgl3OnlyType(String name) {
        if (LWJGL3_AWARE.equals(name)) return false;
        return name.startsWith("org.lwjglx.") || name.startsWith("me.eigenraven.lwjgl3ify.") || name.startsWith("org.lwjgl.sdl.");
    }

    private static boolean isLwjgl3(String name) {
        return isLwjgl3OnlyType(name) || name.startsWith("org.lwjgl.");
    }

    private static boolean isStaticInit(String method) {
        return method.startsWith("<clinit>") || method.startsWith("lambda$static$");
    }
}
