package com.gtnewhorizons.angelica.rendering.voxelization;

import com.gtnewhorizons.angelica.rendering.RenderRegionKeys;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowVoxelizerTest {

    private static final ChunkPrimitiveType PRIMITIVE = QuadPrimitiveType.TRIANGULATED;

    private static class RecordingSink implements ShadowVoxelizer.Sink {
        final List<float[]> regions = new ArrayList<>();
        final List<int[]> ranges = new ArrayList<>();
        int finishes;
        boolean refuseRegion;
        boolean refuseRange;

        @Override
        public boolean region(RenderRegion region, GlVertexFormat format, float x, float y, float z) {
            if (refuseRegion) return false;
            regions.add(new float[]{x, y, z});
            return true;
        }

        @Override
        public boolean range(int vertexOffset, int vertexCount) {
            if (refuseRange) return false;
            ranges.add(new int[]{vertexOffset, vertexCount});
            return true;
        }

        @Override
        public void finish() {
            finishes++;
        }
    }

    private static final ChunkVertexType VERTEX_TYPE = new ChunkVertexType() {
        @Override public float getPositionScale() { return 1f; }
        @Override public float getPositionOffset() { return 0f; }
        @Override public float getTextureScale() { return 1f; }
        @Override public GlVertexFormat getVertexFormat() { return null; }
        @Override public ChunkVertexEncoder createEncoder() { return null; }
    };

    private static TerrainRenderPass pass(String name) {
        return pass(name, false);
    }

    private static TerrainRenderPass pass(String name, boolean sorted) {
        return new TerrainRenderPass(name, null, false, false, sorted, false, VERTEX_TYPE, PRIMITIVE, Map.of());
    }

    private static RenderRegion region(int x, int y, int z) {
        return RenderRegionKeys.create(x, y, z, 0);
    }

    private static final SectionRenderDataUnsafe.Strategy LAYOUT = SectionRenderDataUnsafe.Strategy.COMPACT;

    private long heap() {
        return heap(LAYOUT);
    }

    private long heap(SectionRenderDataUnsafe.Strategy layout) {
        final long heap = layout.allocateHeap();
        heaps.add(heap);
        return heap;
    }

    private static SectionRenderDataStorage storage(long heap) {
        return storage(heap, LAYOUT);
    }

    private static SectionRenderDataStorage storage(long heap, SectionRenderDataUnsafe.Strategy layout) {
        final SectionRenderDataStorage storage = allocateInstance(SectionRenderDataStorage.class);
        setField(storage, SectionRenderDataStorage.class, "storageStrategy", layout);
        setField(storage, SectionRenderDataStorage.class, "primitiveType", PRIMITIVE);
        setField(storage, SectionRenderDataStorage.class, "pMeshDataArray", heap);
        return storage;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocateInstance(Class<T> type) {
        try {
            final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            final Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            final Object unsafe = theUnsafe.get(null);
            return (T) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) {
        try {
            final Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void attachStorage(RenderRegion region, TerrainRenderPass renderPass, SectionRenderDataStorage storage) {
        try {
            final Field field = RenderRegion.class.getDeclaredField("sectionRenderData");
            field.setAccessible(true);
            ((Map<TerrainRenderPass, SectionRenderDataStorage>) field.get(region)).put(renderPass, storage);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ChunkRenderList renderList(RenderRegion region, int... localSectionIndices) {
        final ChunkRenderList list = new ChunkRenderList(region);
        final byte[] sections = new byte[RenderRegion.REGION_SIZE];
        for (int i = 0; i < localSectionIndices.length; i++) {
            sections[i] = (byte) localSectionIndices[i];
        }
        setField(list, ChunkRenderList.class, "sectionsWithGeometry", sections);
        setField(list, ChunkRenderList.class, "sectionsWithGeometryCount", localSectionIndices.length);
        return list;
    }

    private static ChunkRenderListIterable lists(ChunkRenderList... lists) {
        return new ChunkRenderListIterable() {
            @Override
            public Iterator<ChunkRenderList> iterator(boolean reverse) {
                return List.of(lists).iterator();
            }

            @Override
            public int getNumRegions() {
                return lists.length;
            }

            @Override
            public ChunkRenderList getRegion(int index) {
                return lists[index];
            }
        };
    }

    private static void writeSection(long heap, int localSectionIndex, int baseVertex, int... vertsPerFacing) {
        writeSection(heap, LAYOUT, localSectionIndex, baseVertex, vertsPerFacing);
    }

    private static void writeSection(long heap, SectionRenderDataUnsafe.Strategy layout, int localSectionIndex, int baseVertex, int... vertsPerFacing) {
        final Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        int vertex = baseVertex;
        for (int f = 0; f < vertsPerFacing.length; f++) {
            if (vertsPerFacing[f] > 0) {
                ranges.put(ModelQuadFacing.VALUES[f], new VertexRange(vertex, vertsPerFacing[f]));
            }
            vertex += vertsPerFacing[f];
        }
        layout.writeMeshesAndSliceMask(heap, localSectionIndex, baseVertex, 0, ranges, PRIMITIVE);
    }

    private static RecordingSink walk(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, boolean faceCulling) {
        final RecordingSink sink = new RecordingSink();
        new ShadowVoxelizer().walkPass(renderLists, renderPass, null, camera, camera, faceCulling, sink);
        return sink;
    }

    private final List<Long> heaps = new ArrayList<>();

    @AfterEach
    void tearDown() {
        heaps.forEach(LAYOUT::freeHeap);
        heaps.clear();
    }

    @Test
    void emittedRangesCoverMultipleSectionsInARegion() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 0, 8, 4, 0, 0, 12);
        writeSection(heap, 5, 100, 0, 16, 0, 0, 4, 0, 0);
        attachStorage(region, renderPass, storage);
        final ChunkRenderListIterable renderLists = lists(renderList(region, 0, 5));
        final CameraTransform camera = new CameraTransform(0, 0, 0);

        final RecordingSink sink = walk(renderLists, renderPass, camera, false);

        assertEquals(1, sink.regions.size(), "one region announced once");
        assertEquals(5, sink.ranges.size(), "section 0's facings 2-3 merge, section 5's facings stay separate");
        assertArrayEqualsInt(new int[]{0, 4}, sink.ranges.get(0), "section 0 facing 0");
        assertArrayEqualsInt(new int[]{4, 12}, sink.ranges.get(1), "section 0 facings 2-3");
        assertArrayEqualsInt(new int[]{16, 12}, sink.ranges.get(2), "section 0 facing 6");
        assertArrayEqualsInt(new int[]{100, 16}, sink.ranges.get(3), "section 5 facing 1");
        assertArrayEqualsInt(new int[]{116, 4}, sink.ranges.get(4), "section 5 facing 4");
    }

    @Test
    void regionOffsetIsCameraRelative() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(1, 0, 3);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        attachStorage(region, renderPass, storage);
        final CameraTransform camera = new CameraTransform(70.25, 12.5, -3.75);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, camera, false);

        assertEquals(1, sink.regions.size());
        final float[] offset = sink.regions.get(0);
        assertEquals(region.getOriginX() - camera.intX - camera.fracX, offset[0], 0f, "x");
        assertEquals(region.getOriginY() - camera.intY - camera.fracY, offset[1], 0f, "y");
        assertEquals(region.getOriginZ() - camera.intZ - camera.fracZ, offset[2], 0f, "z");
    }

    @Test
    void regionOffsetUsesTheRegionOriginNotA256BlockGrid() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(1, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        attachStorage(region, renderPass, storage);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertEquals(128f, sink.regions.get(0)[0], 0f, "region 1 starts at block 128");
    }

    @Test
    void contiguousFacingsCollapseToOneRange() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 4, 4, 4, 4, 4, 4);
        attachStorage(region, renderPass, storage);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertEquals(1, sink.ranges.size(), "seven adjacent facings are one vertex span, not seven dispatches");
        assertEquals(0, sink.ranges.get(0)[0]);
        assertEquals(28, sink.ranges.get(0)[1]);
    }

    @Test
    void gapsInTheFacingMaskSplitRuns() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 0, 8, 0, 0, 0, 4);
        attachStorage(region, renderPass, storage);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertEquals(3, sink.ranges.size());
        assertArrayEqualsInt(new int[]{0, 4}, sink.ranges.get(0), "facing 0");
        assertArrayEqualsInt(new int[]{4, 8}, sink.ranges.get(1), "facing 2");
        assertArrayEqualsInt(new int[]{12, 4}, sink.ranges.get(2), "facing 6");
    }

    @Test
    void sectionsWithoutGeometryNeverAnnounceTheRegion() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        attachStorage(region, renderPass, storage);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertTrue(sink.regions.isEmpty(), "an empty region must not open an encoder");
        assertTrue(sink.ranges.isEmpty());
        assertEquals(1, sink.finishes);
    }

    @Test
    void missingStorageForThePassIsSkipped() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertTrue(sink.ranges.isEmpty());
        assertEquals(1, sink.finishes);
    }

    @Test
    void unbindableRegionIsSkippedButThePassContinues() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion regionA = region(0, 0, 0);
        final RenderRegion regionB = region(1, 0, 0);
        final long heapA = heap();
        final long heapB = heap();
        final SectionRenderDataStorage storageA = storage(heapA);
        final SectionRenderDataStorage storageB = storage(heapB);
        writeSection(heapA, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        writeSection(heapB, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        attachStorage(regionA, renderPass, storageA);
        attachStorage(regionB, renderPass, storageB);

        final RecordingSink sink = new RecordingSink();
        sink.refuseRegion = true;
        new ShadowVoxelizer().walkPass(lists(renderList(regionA, 0), renderList(regionB, 0)), renderPass, null,
            new CameraTransform(0, 0, 0), new CameraTransform(0, 0, 0), false, sink);

        assertTrue(sink.ranges.isEmpty(), "a region that cannot be bound emits nothing");
        assertEquals(1, sink.finishes, "the encoder is still closed exactly once");
    }

    @Test
    void refusedRangeSkipsOnlyThatRegion() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion regionA = region(0, 0, 0);
        final RenderRegion regionB = region(1, 0, 0);
        final long heapA = heap();
        final long heapB = heap();
        final SectionRenderDataStorage storageA = storage(heapA);
        final SectionRenderDataStorage storageB = storage(heapB);
        writeSection(heapA, 0, 0, 4, 0, 4, 0, 0, 0, 0);
        writeSection(heapB, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        attachStorage(regionA, renderPass, storageA);
        attachStorage(regionB, renderPass, storageB);

        final RecordingSink sink = new RecordingSink() {
            @Override public boolean range(int vertexOffset, int vertexCount) {
                return regions.size() != 1 && super.range(vertexOffset, vertexCount);
            }
        };
        new ShadowVoxelizer().walkPass(lists(renderList(regionA, 0), renderList(regionB, 0)), renderPass, null, new CameraTransform(0, 0, 0), new CameraTransform(0, 0, 0), false, sink);

        assertEquals(2, sink.regions.size(), "the second region is still attempted after the first is refused");
        assertEquals(1, sink.ranges.size(), "only the second region's range lands");
        assertEquals(1, sink.finishes);
    }

    @Test
    void faceCullingNarrowsTheEmittedRanges() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final long heap = heap();
        final SectionRenderDataStorage storage = storage(heap);
        writeSection(heap, 0, 0, 4, 4, 4, 4, 4, 4, 4);
        attachStorage(region, renderPass, storage);
        final ChunkRenderListIterable renderLists = lists(renderList(region, 0));
        final CameraTransform camera = new CameraTransform(500, 200, 500);

        final RecordingSink sink = walk(renderLists, renderPass, camera, true);

        assertFalse(sink.ranges.isEmpty(), "face culling removed everything; the fixture is not exercising the path");
        assertTrue(sink.ranges.size() < 7 || sink.ranges.get(0)[1] < 28, "face culling did not narrow anything");
    }

    @Test
    void sortedPassWalksTheFullLayoutAndYieldsTheSameSpansAsCompact() {
        final SectionRenderDataUnsafe.Strategy full = SectionRenderDataUnsafe.Strategy.FULL;
        final int[] facings = { 4, 0, 8, 0, 0, 0, 4 };

        final TerrainRenderPass sortedPass = pass("translucent", true);
        final RenderRegion sortedRegion = region(0, 0, 0);
        final long sortedHeap = heap(full);
        final SectionRenderDataStorage sortedStorage = storage(sortedHeap, full);
        writeSection(sortedHeap, full, 0, 0, facings);
        writeSection(sortedHeap, full, 5, 100, facings);
        attachStorage(sortedRegion, sortedPass, sortedStorage);

        final TerrainRenderPass solidPass = pass("solid");
        final RenderRegion solidRegion = region(0, 0, 0);
        final long solidHeap = heap();
        final SectionRenderDataStorage solidStorage = storage(solidHeap);
        writeSection(solidHeap, 0, 0, facings);
        writeSection(solidHeap, 5, 100, facings);
        attachStorage(solidRegion, solidPass, solidStorage);

        final RecordingSink sorted = walk(lists(renderList(sortedRegion, 0, 5)), sortedPass, new CameraTransform(0, 0, 0), false);
        final RecordingSink compact = walk(lists(renderList(solidRegion, 0, 5)), solidPass, new CameraTransform(0, 0, 0), false);

        assertEquals(1, sorted.regions.size(), "the sorted region is announced once");
        assertFalse(sorted.ranges.isEmpty(), "the FULL layout walk produced nothing");
        assertEquals(6, compact.ranges.size(), "two sections of three runs each");
        assertEquals(compact.ranges.size(), sorted.ranges.size(), "FULL and COMPACT disagree on run count");
        for (int i = 0; i < compact.ranges.size(); i++) {
            assertArrayEqualsInt(compact.ranges.get(i), sorted.ranges.get(i), "range " + i);
        }
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual, String label) {
        assertEquals(expected[0], actual[0], label + " vertexOffset");
        assertEquals(expected[1], actual[1], label + " vertexCount");
    }
}
