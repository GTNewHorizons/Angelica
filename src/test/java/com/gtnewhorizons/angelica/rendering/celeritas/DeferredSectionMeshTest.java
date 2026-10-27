package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedAngelicaChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.gtnewhorizons.angelica.rendering.celeritas.DeferredSectionMesh.DeferredCapture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return new DeferredSectionMesh(task, token, null, null, null, null, null, 0L, false, 0L, 0L);
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

    @Test
    void captureCopiesBlocks() {
        final int[] counts = {4, 12};
        final DeferredCapture capture = new DeferredCapture();
        final IntArrayList expectedRaw = new IntArrayList();
        final IntArrayList expectedStates = new IntArrayList();
        final IntArrayList expectedShader = new IntArrayList();
        final IntArrayList expectedStart = new IntArrayList();
        int total = 0;
        for (int b = 0; b < counts.length; b++) {
            final int n = counts[b];
            final int[] raw = new int[n * AngelicaChunkBuildContext.INTS_PER_VERTEX + 5];
            final int[] states = new int[n + 3];
            final int[] shader = new int[n + 3];
            for (int i = 0; i < raw.length; i++) raw[i] = b * 100000 + i;
            for (int i = 0; i < states.length; i++) {
                states[i] = b * 1000 + 500000 + i;
                shader[i] = b * 1000 + 900000 + i;
            }
            capture.add(b, raw, n, states, shader, 7000 + b);
            expectedStart.add(total);
            for (int i = 0; i < n * AngelicaChunkBuildContext.INTS_PER_VERTEX; i++) expectedRaw.add(raw[i]);
            for (int i = 0; i < n; i++) {
                expectedStates.add(states[i]);
                expectedShader.add(shader[i]);
            }
            total += n;
        }

        assertEquals(counts.length, capture.blockCount());
        assertEquals(total, capture.vertexCount());
        assertEquals(expectedStart, capture.blockStart);
        assertEquals(expectedRaw, capture.raw);
        assertEquals(expectedStates, capture.vertexStates);
        assertEquals(expectedShader, capture.shaderBlockIds);
        for (int b = 0; b < counts.length; b++) {
            assertEquals(b, capture.blockIndex.getInt(b));
            assertEquals(counts[b], capture.blockVertexCount.getInt(b));
            assertEquals(7000 + b, capture.teBlockIds.getInt(b));
        }
    }

    @Test
    void clearEmptiesAndTrimsOversizedLists() {
        final DeferredCapture capture = new DeferredCapture();
        final int vertices = DeferredCapture.VERTEX_TRIM + 1000;
        capture.add(0, new int[vertices * AngelicaChunkBuildContext.INTS_PER_VERTEX], vertices, new int[vertices], new int[vertices], 1);
        assertTrue(capture.vertexStates.elements().length > DeferredCapture.VERTEX_TRIM);

        capture.clear();

        assertEquals(0, capture.raw.size());
        assertTrue(capture.raw.elements().length <= DeferredCapture.VERTEX_TRIM * AngelicaChunkBuildContext.INTS_PER_VERTEX);
        for (IntArrayList list : new IntArrayList[] {capture.vertexStates, capture.shaderBlockIds}) {
            assertEquals(0, list.size());
            assertTrue(list.elements().length <= DeferredCapture.VERTEX_TRIM);
        }
    }
}
