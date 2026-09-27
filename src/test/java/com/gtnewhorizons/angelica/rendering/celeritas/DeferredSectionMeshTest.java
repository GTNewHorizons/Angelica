package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedAngelicaChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new DeferredSectionMesh(task, token, null, null, null, null, null, 0L, false, 0L);
    }

    @Test
    void freshSectionIsNotObsolete() {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        assertFalse(mesh(section, 5, false).isObsolete());
    }

    @Test
    void sameBuiltFrameIsNotObsolete() {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        section.setLastBuiltFrame(5);
        assertFalse(mesh(section, 5, false).isObsolete());
    }

    @Test
    void newerBuiltFrameIsObsolete() {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        section.setLastBuiltFrame(6);
        assertTrue(mesh(section, 5, false).isObsolete());
    }

    @Test
    void cancelledTokenIsObsolete() {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        assertTrue(mesh(section, 5, true).isObsolete());
    }

    @Test
    void deletedSectionIsObsolete() {
        final RenderSection section = new RenderSection(null, 0, 0, 0);
        section.delete();
        assertTrue(mesh(section, 5, false).isObsolete());
    }
}
