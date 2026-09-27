package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.sorting.CutPlaneIndex;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class TreeSortCameraBookkeepingGLTest {

    private static final Frustum ALWAYS_VISIBLE = (minX, minY, minZ, maxX, maxY, maxZ) -> true;

    private static CommandList commandList;

    private TestRsm rsm;
    private RenderSection section;
    private int frame;

    private static final class TestRsm extends RenderSectionManager {
        boolean inShadowPass;

        TestRsm(RenderPassConfiguration<?> config, CommandList commandList) {
            // -1 threads: no workers; SORT entries stay queued since updateChunks() is never called
            super(config, () -> new ChunkBuildContext(config), (device, c) -> new NoopRenderer(c), 2, commandList, 0, 16, -1, true);
        }

        @Override protected AsyncOcclusionMode getAsyncOcclusionMode() { return AsyncOcclusionMode.NONE; }
        @Override protected boolean useFogOcclusion() { return false; }
        @Override protected boolean useRasterOcclusionCulling() { return false; }
        @Override protected boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator) { return false; }
        @Override public FogService getFogService() { throw new UnsupportedOperationException(); }
        @Override protected boolean isSectionVisuallyEmpty(int x, int y, int z) { return true; }
        @Override protected ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) { return null; }
        @Override public boolean isInShadowPass() { return this.inShadowPass; }

        Vector3d cameraPosition() { return this.cameraPosition; }
        Vector3d previousCameraPosition() { return this.previousCameraPosition; }
        RenderListManager terrainLists() { return this.renderListManager; }
        RenderListManager shadowLists() { return this.shadowRenderListManager; }
    }

    private record NoopRenderer(RenderPassConfiguration<?> getRenderPassConfiguration) implements ChunkRenderer {
        @Override
        public void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera) {}

        @Override
        public void delete(CommandList commandList) {}
    }

    @BeforeAll
    static void setUpDevice() {
        RenderDevice.enterManagedCode();
        commandList = RenderDevice.INSTANCE.createCommandList();
    }

    @AfterAll
    static void tearDownDevice() {
        RenderDevice.exitManagedCode();
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        final TerrainRenderPass solid = new TerrainRenderPass("tree_sort_solid", TerrainRenderPass.PipelineState.DEFAULT, false, false, false, false, ChunkMeshFormats.VANILLA_LIKE, QuadPrimitiveType.TRIANGULATED, Map.of());
        final TerrainRenderPass translucent = new TerrainRenderPass("tree_sort_translucent", TerrainRenderPass.PipelineState.DEFAULT, true, false, true, false, ChunkMeshFormats.VANILLA_LIKE, QuadPrimitiveType.TRIANGULATED, Map.of());
        final Material solidMaterial = new Material(solid, AlphaCutoffParameter.ZERO, false);
        final Material translucentMaterial = new Material(translucent, AlphaCutoffParameter.ZERO, true);
        final RenderPassConfiguration<Object> config = new RenderPassConfiguration<>(Map.of(), Map.of("solid", List.of(solid), "translucent", List.of(translucent)), solidMaterial, solidMaterial, translucentMaterial);

        rsm = new TestRsm(config, commandList);
        rsm.onSectionAdded(0, 0, 0);
        section = rsm.getAllRenderSections().iterator().next();

        final Field f = RenderSectionManager.class.getDeclaredField("cutPlaneIndex");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        final CutPlaneIndex<RenderSection> index = (CutPlaneIndex<RenderSection>) f.get(rsm);
        index.put(section, new float[][] { { 8f }, {}, {} }, section.getOriginX(), section.getOriginY(), section.getOriginZ());
        assertEquals(1, index.size());
    }

    @AfterEach
    void tearDown() {
        if (rsm != null) rsm.destroy();
        rsm = null;
    }

    private static Viewport at(double x) {
        return new Viewport(ALWAYS_VISIBLE, new Vector3d(x, 8.5, 8.5));
    }

    private static Viewport farShadowViewport() {
        return new Viewport(ALWAYS_VISIBLE, new Vector3d(520.5, 100.5, 520.5));
    }

    private void update(double x) {
        rsm.update(at(x), ++frame, false);
    }

    private void shadowThenMain(double x) {
        rsm.inShadowPass = true;
        rsm.updateForShadowPass(at(x), farShadowViewport(), ++frame, false);
        rsm.inShadowPass = false;
        assertTrue(rsm.didShadowPassRunThisFrame());
        rsm.update(at(x), frame, false);
        assertFalse(rsm.didShadowPassRunThisFrame());
    }

    private long queued(RenderListManager lists, ChunkUpdateType type) {
        return lists.getRebuildLists().byUpdateType().get(type).stream().filter(s -> s == section).count();
    }

    private void assertSortQueuedInTerrain() {
        assertEquals(ChunkUpdateType.SORT, section.getPendingUpdate());
        assertEquals(1, queued(rsm.terrainLists(), ChunkUpdateType.SORT));
        assertEquals(0, queued(rsm.shadowLists(), ChunkUpdateType.SORT));
    }

    @Test
    void crossingSchedulesSort() {
        update(7.5);
        assertNull(section.getPendingUpdate());

        update(8.5);
        assertNotNull(rsm.previousCameraPosition());
        assertNotSame(rsm.previousCameraPosition(), rsm.cameraPosition());
        assertEquals(7.5, rsm.previousCameraPosition().x);
        assertEquals(8.5, rsm.cameraPosition().x);
        assertSortQueuedInTerrain();
    }

    @Test
    void shadowFrameKeepsPlayerCameraBookkeeping() {
        update(7.5);

        shadowThenMain(8.5);
        assertEquals(new Vector3d(8.5, 8.5, 8.5), rsm.cameraPosition());
        assertEquals(new Vector3d(7.5, 8.5, 8.5), rsm.previousCameraPosition());
        assertSortQueuedInTerrain();

        shadowThenMain(8.5);
        assertNotSame(rsm.previousCameraPosition(), rsm.cameraPosition());
        assertEquals(new Vector3d(8.5, 8.5, 8.5), rsm.previousCameraPosition());
        assertEquals(new Vector3d(8.5, 8.5, 8.5), rsm.cameraPosition());
    }
}
