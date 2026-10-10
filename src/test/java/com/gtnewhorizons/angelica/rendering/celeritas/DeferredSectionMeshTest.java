package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedAngelicaChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferredSectionMeshTest {

    private static DeferredSectionMesh mesh(RenderSection section, int buildTime, boolean cancelled) {
        final ThreadedAngelicaChunkBuilderMeshingTask task = new ThreadedAngelicaChunkBuilderMeshingTask(section, null, buildTime, new Vector3d());
        final CancellationToken token = new CancellationToken() {
            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public void setCancelled() {}
        };
        return new DeferredSectionMesh(task, token, null, null, null, null, null, 0L, false, 0L);
    }

    static Stream<Arguments> sections() {
        return Stream.of(
            Arguments.of("fresh section", (Consumer<RenderSection>) section -> {}, false, false),
            Arguments.of("same built frame", (Consumer<RenderSection>) section -> section.setLastBuiltFrame(5), false, false),
            Arguments.of("newer built frame", (Consumer<RenderSection>) section -> section.setLastBuiltFrame(6), false, true),
            Arguments.of("cancelled token", (Consumer<RenderSection>) section -> {}, true, true),
            Arguments.of("deleted section", (Consumer<RenderSection>) RenderSection::delete, false, true));
    }

    @ParameterizedTest(name = "{0} -> obsolete={3}")
    @MethodSource("sections")
    void isObsolete(String name, Consumer<RenderSection> setup, boolean cancelled, boolean expected) {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        setup.accept(section);
        assertEquals(expected, mesh(section, 5, cancelled).isObsolete());
    }
}
