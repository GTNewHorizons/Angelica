package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.config.GpuCullingMode;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.mixins.interfaces.SectionRenderDataStorageRegionAccessor;
import com.gtnewhorizons.angelica.rendering.RenderRegionKeys;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderPassConfiguration;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@GLCoreTest
class TerrainPassPreparationTest {
    private final TerrainRenderPass[] previous = new TerrainRenderPass[3];
    private final TerrainRenderPass[] passes = new TerrainRenderPass[3];
    private final Storage[] storage = new Storage[3];
    private final CameraTransform camera = new CameraTransform(0, 0, 0);
    private final ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
    private GpuCullingMode previousMode;
    private GpuTerrainCuller gpu;
    private GpuDrivenChunkCuller nativeCuller;
    private SectionMetaBuffer meta;
    private RenderRegion region;
    private ChunkRenderList list;

    private static final class Storage extends SectionRenderDataStorage implements SectionRenderDataStorageRegionAccessor {
        private RenderRegion region;
        private int passIndex;
        private final int[] slots = new int[RenderRegion.REGION_SIZE];

        Storage(boolean sorted) {
            super(QuadPrimitiveType.TRIANGULATED, sorted, 1);
            final SectionRenderDataUnsafe.Strategy layout = sorted ? SectionRenderDataUnsafe.Strategy.FULL : SectionRenderDataUnsafe.Strategy.COMPACT;
            layout.writeMeshesAndSliceMask((long) field(this, SectionRenderDataStorage.class, "pMeshDataArray"), 0, 0, 0,
                Map.of(ModelQuadFacing.VALUES[0], new VertexRange(0, 4)), QuadPrimitiveType.TRIANGULATED);
        }

        @Override public RenderRegion angelica$getRegion() { return region; }
        @Override public void angelica$setRegion(RenderRegion value) { region = value; }
        @Override public int angelica$getPassIndex() { return passIndex; }
        @Override public void angelica$setPassIndex(int value) { passIndex = value; }
        @Override public int[] angelica$getSlotCache() { return slots; }
    }

    private static Object field(Object target, Class<?> owner, String name) {
        try {
            final Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) {
        try {
            final Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object state(String name) {
        return field(gpu, GpuTerrainCuller.class, name);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        previous[0] = AngelicaRenderPassConfiguration.SOLID_PASS;
        previous[1] = AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS;
        previous[2] = AngelicaRenderPassConfiguration.TRANSLUCENT_PASS;
        previousMode = GpuCulling.mode();
        for (int i = 0; i < 3; i++) {
            passes[i] = new TerrainRenderPass("preparation" + i, null, false, i == 2, i == 2, false, ChunkMeshFormats.VANILLA_LIKE, QuadPrimitiveType.TRIANGULATED, Map.of());
        }
        AngelicaRenderPassConfiguration.SOLID_PASS = passes[0];
        AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS = passes[1];
        AngelicaRenderPassConfiguration.TRANSLUCENT_PASS = passes[2];
        GpuCulling.setMode(GpuCullingMode.COMPUTE);
        nativeCuller = new GpuDrivenChunkCuller();
        set(nativeCuller, GpuDrivenChunkCuller.class, "disabled", true);
        meta = new SectionMetaBuffer();
        gpu = new GpuTerrainCuller(nativeCuller, meta);
        region = RenderRegionKeys.create(0, 0, 0, 0);
        final Map<TerrainRenderPass, SectionRenderDataStorage> attached = (Map<TerrainRenderPass, SectionRenderDataStorage>) field(region, RenderRegion.class, "sectionRenderData");
        for (int i = 0; i < 3; i++) {
            storage[i] = new Storage(i == 2);
            attached.put(passes[i], storage[i]);
        }
        list = new ChunkRenderList(region);
        set(list, ChunkRenderList.class, "sectionsWithGeometry", new byte[]{0});
        set(list, ChunkRenderList.class, "sectionsWithGeometryCount", 1);
    }

    @AfterEach
    void cleanup() {
        if (gpu != null) gpu.delete();
        if (meta != null) meta.shutdown();
        if (nativeCuller != null) nativeCuller.shutdown();
        for (Storage value : storage) if (value != null) value.delete();
        AngelicaRenderPassConfiguration.SOLID_PASS = previous[0];
        AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS = previous[1];
        AngelicaRenderPassConfiguration.TRANSLUCENT_PASS = previous[2];
        GpuCulling.setMode(previousMode);
    }

    private ChunkRenderListIterable lists(int mask) {
        return new ChunkRenderListIterable() {
            @Override public Iterator<ChunkRenderList> iterator(boolean reverse) { return List.of(list).iterator(); }
            @Override public int getNumRegions() { return 1; }
            @Override public ChunkRenderList getRegion(int index) { return list; }
            @Override public boolean hasPass(TerrainRenderPass pass) {
                for (int i = 0; i < passes.length; i++)
                    if (passes[i] == pass)
                        return (mask & (1 << i)) != 0;
                return false;
            }
        };
    }

    private void prepare(ChunkRenderListIterable lists) {
        gpu.prepareAllPasses(matrices, lists, camera, camera, false);
    }

    private void select(ChunkRenderListIterable lists, int pass) {
        gpu.beginRenderPass(matrices, lists, passes[pass], camera, camera, false);
    }

    private void assertRange(String pass, int base) {
        final Object expected = state(pass);
        assertSame(expected, state("current"));
        assertEquals(base, field(expected, expected.getClass(), "entryBase"));
        assertEquals(1, field(expected, expected.getClass(), "entryCount"));
        assertEquals(((long) base << 32) | 1L, ((long[]) field(expected, expected.getClass(), "regionRanges"))[0]);
    }

    @Test
    void preparesAndSelectsAllEightPassCombinations() {
        for (int mask = 0; mask < 8; mask++) {
            final ChunkRenderListIterable lists = lists(mask);
            prepare(lists);
            if (mask == 0) {
                assertNull(state("preparedRenderLists"));
                assertFalse(gpu.isComputeActiveThisPass());
                continue;
            }
            assertEquals(Integer.bitCount(mask), state("totalAppendedEntries"));
            int base = 0;
            for (int pass = 0; pass < 3; pass++) {
                if ((mask & (1 << pass)) == 0) {
                    select(lists, pass);
                    gpu.endPass();
                    continue;
                }
                final int entries = (int) state("totalAppendedEntries");
                select(lists, pass);
                assertRange(pass == 2 ? "sortedPass" : pass == 1 && (mask & 1) != 0 ? "secondPass" : "primaryPass", base++);
                assertEquals(entries, state("totalAppendedEntries"));
                gpu.endPass();
            }
        }
    }

    @Test
    void differentListsRebuildCutoutAndInvalidateSortedCommands() {
        prepare(lists(7));
        final ChunkRenderListIterable replacement = lists(6);
        select(replacement, 1);
        assertRange("primaryPass", 0);
        assertEquals(1, state("totalAppendedEntries"));
        assertFalse(gpu.selectPreparedSortedPass());
        assertFalse(gpu.selectPreparedSecondPass());
        assertSame(replacement, state("preparedRenderLists"));
        gpu.endPass();
        select(replacement, 2);
        assertRange("primaryPass", 0);
        assertFalse(gpu.selectPreparedSortedPass());
    }

    @Test
    void emptyPreparationInvalidatesEveryPreviouslyPreparedPass() {
        prepare(lists(7));
        prepare(lists(0));
        assertFalse(gpu.selectPreparedPrimaryPass());
        assertFalse(gpu.selectPreparedSecondPass());
        assertFalse(gpu.selectPreparedSortedPass());
        assertNull(state("preparedPrimaryPass"));
        assertNull(state("preparedRenderLists"));
    }

    @Test
    void standaloneRebuildRetainsCombinedCutoutReuse() {
        prepare(lists(4));
        final ChunkRenderListIterable replacement = lists(3);
        select(replacement, 0);
        assertRange("primaryPass", 0);
        assertFalse(gpu.selectPreparedSortedPass());
        gpu.endPass();
        select(replacement, 1);
        assertRange("secondPass", 1);
        assertEquals(2, state("totalAppendedEntries"));
    }

    @Test
    void absentSolidSelectionPreservesPreparedCutoutAndSortedPasses() {
        final ChunkRenderListIterable lists = lists(6);
        prepare(lists);
        select(lists, 0);
        gpu.endPass();
        select(lists, 1);
        assertRange("primaryPass", 0);
        gpu.endPass();
        select(lists, 2);
        assertRange("sortedPass", 1);
    }
    @Test
    void independentlyResetCommandStorageCannotSelectOldPreparation() {
        prepare(lists(7));
        gpu.beginCullPass(0);
        assertFalse(gpu.selectPreparedPrimaryPass());
        assertFalse(gpu.selectPreparedSecondPass());
        assertFalse(gpu.selectPreparedSortedPass());
        assertNull(state("preparedRenderLists"));
    }

    @Test
    void failedSortedPreparationDoesNotPublishPartiallyBuiltPasses() {
        final IllegalStateException failure = new IllegalStateException("sorted walk failed");
        final ChunkRenderListIterable lists = new ChunkRenderListIterable() {
            private int walks;
            @Override public Iterator<ChunkRenderList> iterator(boolean reverse) { return List.of(list).iterator(); }
            @Override public int getNumRegions() {
                if (++walks == 3) throw failure;
                return 1;
            }
            @Override public ChunkRenderList getRegion(int index) { return list; }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> prepare(lists)));
        assertFalse(gpu.selectPreparedPrimaryPass());
        assertFalse(gpu.selectPreparedSecondPass());
        assertFalse(gpu.selectPreparedSortedPass());
        assertNull(state("preparedRenderLists"));
    }

}
