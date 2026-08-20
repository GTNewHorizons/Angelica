package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.ONCE;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestCollectingHolderTest {

    private static void declare(UniformHolder h) {
        final Vector3fc zero = new Vector3f();
        h.uniform1f(PER_FRAME, "frameTimeCounter", () -> 0.0f)
            .uniform1f(ONCE, "pi", () -> 3.14159f)
            .uniform3f(PER_FRAME, "cameraPosition", () -> zero)
            .uniform1i(PER_TICK, "worldTime", () -> 0)
            .uniform1f(PER_FRAME, "rainStrength", () -> 0.0f)
            .externallyManagedUniform("iris_ModelViewMatrix", UniformType.MAT4);
    }

    private static UniformManifest harvest() {
        final ManifestCollectingHolder h = new ManifestCollectingHolder();
        declare(h);
        return h.build();
    }

    @Test
    void groupsByFrequencyPreservingRegistrationOrderWithinGroup() {
        final List<UniformManifest.Entry> e = harvest().entries();

        assertEquals(List.of("pi", "worldTime", "frameTimeCounter", "cameraPosition", "rainStrength"),
            e.stream().map(UniformManifest.Entry::name).toList(),
            "ONCE then PER_TICK then PER_FRAME, declaration order inside each group");

        assertEquals(List.of(ONCE, PER_TICK, PER_FRAME, PER_FRAME, PER_FRAME),
            e.stream().map(UniformManifest.Entry::frequency).toList());
    }

    @Test
    void capturesDeclaredTypes() {
        final UniformManifest m = harvest();
        assertEquals(UniformType.VEC3, entry(m, "cameraPosition").type());
        assertEquals(UniformType.FLOAT, entry(m, "frameTimeCounter").type());
        assertEquals(UniformType.INT, entry(m, "worldTime").type());
    }

    @Test
    void isDeterministicAcrossHarvests() {
        assertEquals(harvest().entries(), harvest().entries());
    }

    @Test
    void externallyManagedUniformsStayOutOfTheBlock() {
        final UniformManifest m = harvest();
        assertTrue(m.externallyManagedNames().contains("iris_ModelViewMatrix"));
        assertFalse(m.entries().stream().anyMatch(e -> e.name().equals("iris_ModelViewMatrix")), "per-draw matrices are written by the backend and must remain loose uniforms");
    }

    @Test
    void duplicateDeclarationIsDroppedNotDuplicated() {
        final ManifestCollectingHolder h = new ManifestCollectingHolder();
        h.uniform1f(PER_FRAME, "rainStrength", () -> 0.0f);
        h.uniform1f(ONCE, "rainStrength", () -> 1.0f);

        final List<UniformManifest.Entry> e = h.build().entries();
        assertEquals(1, e.size());
        assertEquals(PER_FRAME, e.get(0).frequency(), "first declaration wins");
    }

    private static UniformManifest.Entry entry(UniformManifest m, String name) {
        return m.entries().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }
}
