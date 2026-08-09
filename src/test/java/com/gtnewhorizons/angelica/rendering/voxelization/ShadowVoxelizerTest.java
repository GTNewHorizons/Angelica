package com.gtnewhorizons.angelica.rendering.voxelization;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.config.GpuCullingMode;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
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
        return new TerrainRenderPass(name, null, false, false, false, false, VERTEX_TYPE, PRIMITIVE, Map.of());
    }

    private static RenderRegion region(int x, int y, int z) {
        try {
            for (Constructor<?> ctor : RenderRegion.class.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 5) {
                    ctor.setAccessible(true);
                    return (RenderRegion) ctor.newInstance(x, y, z, 0, null);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("RenderRegion constructor shape changed");
    }

    private SectionRenderDataStorage storage() {
        final SectionRenderDataUnsafe.Strategy layout = SectionRenderDataUnsafe.Strategy.COMPACT;
        final int bytes = (int) layout.getStride() * RenderRegion.REGION_SIZE;
        final ByteBuffer rows = MemoryUtilities.memAlloc(bytes).order(ByteOrder.nativeOrder());
        for (int i = 0; i < bytes; i++) rows.put(i, (byte) 0);
        allocated.add(rows);

        final SectionRenderDataStorage storage = allocateInstance(SectionRenderDataStorage.class);
        setField(storage, SectionRenderDataStorage.class, "storageStrategy", layout);
        setField(storage, SectionRenderDataStorage.class, "primitiveType", PRIMITIVE);
        setField(storage, SectionRenderDataStorage.class, "pMeshDataArray", MemoryUtilities.memAddress(rows));
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
        return reverse -> List.of(lists).iterator();
    }

    private static void writeSection(SectionRenderDataStorage storage, int localSectionIndex, int baseVertex, int... vertsPerFacing) {
        final Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        int vertex = baseVertex;
        for (int f = 0; f < vertsPerFacing.length; f++) {
            if (vertsPerFacing[f] > 0) {
                ranges.put(ModelQuadFacing.VALUES[f], new VertexRange(vertex, vertsPerFacing[f]));
            }
            vertex += vertsPerFacing[f];
        }
        storage.getStorageStrategy().writeMeshes(storage.getDataPointer(localSectionIndex), baseVertex, 0, ranges, PRIMITIVE);
    }

    private static RecordingSink walk(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, boolean faceCulling) {
        final RecordingSink sink = new RecordingSink();
        new ShadowVoxelizer().walkPass(renderLists, renderPass, null, camera, camera, faceCulling, sink);
        return sink;
    }

    private final List<ByteBuffer> allocated = new ArrayList<>();

    @AfterEach
    void tearDown() {
        GpuCulling.setMode(GpuCullingMode.CPU_ONLY);
        allocated.forEach(MemoryUtilities::memFree);
        allocated.clear();
    }

    @Test
    void emittedRangesAreIdenticalAcrossCullModes() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 0, 8, 4, 0, 0, 12);
        writeSection(storage, 5, 100, 0, 16, 0, 0, 4, 0, 0);
        attachStorage(region, renderPass, storage);
        final ChunkRenderListIterable renderLists = lists(renderList(region, 0, 5));
        final CameraTransform camera = new CameraTransform(0, 0, 0);

        GpuCulling.setMode(GpuCullingMode.CPU_ONLY);
        final RecordingSink cpu = walk(renderLists, renderPass, camera, false);
        GpuCulling.setMode(GpuCullingMode.COMPUTE);
        final RecordingSink compute = walk(renderLists, renderPass, camera, false);

        assertFalse(cpu.ranges.isEmpty(), "the walk produced nothing to compare");
        assertEquals(cpu.ranges.size(), compute.ranges.size(), "range count differs between cull modes");
        for (int i = 0; i < cpu.ranges.size(); i++) {
            assertArrayEqualsInt(cpu.ranges.get(i), compute.ranges.get(i), "range " + i);
        }
        assertEquals(1, cpu.regions.size(), "one region announced once");
        assertEquals(1, compute.regions.size());
    }

    @Test
    void regionOffsetIsCameraRelative() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(1, 0, 3);
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 0, 0, 0, 0, 0, 0);
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
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        attachStorage(region, renderPass, storage);

        final RecordingSink sink = walk(lists(renderList(region, 0)), renderPass, new CameraTransform(0, 0, 0), false);

        assertEquals(128f, sink.regions.get(0)[0], 0f, "region 1 starts at block 128");
    }

    @Test
    void contiguousFacingsCollapseToOneRange() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 4, 4, 4, 4, 4, 4);
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
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 0, 8, 0, 0, 0, 4);
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
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
        final SectionRenderDataStorage storageA = storage();
        final SectionRenderDataStorage storageB = storage();
        writeSection(storageA, 0, 0, 4, 0, 0, 0, 0, 0, 0);
        writeSection(storageB, 0, 0, 4, 0, 0, 0, 0, 0, 0);
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
        final SectionRenderDataStorage storageA = storage();
        final SectionRenderDataStorage storageB = storage();
        writeSection(storageA, 0, 0, 4, 0, 4, 0, 0, 0, 0);
        writeSection(storageB, 0, 0, 4, 0, 0, 0, 0, 0, 0);
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
    void faceCullingNarrowsTheEmittedRangesTheSameWayInBothModes() {
        final TerrainRenderPass renderPass = pass("solid");
        final RenderRegion region = region(0, 0, 0);
        final SectionRenderDataStorage storage = storage();
        writeSection(storage, 0, 0, 4, 4, 4, 4, 4, 4, 4);
        attachStorage(region, renderPass, storage);
        final ChunkRenderListIterable renderLists = lists(renderList(region, 0));
        final CameraTransform camera = new CameraTransform(500, 200, 500);

        GpuCulling.setMode(GpuCullingMode.CPU_ONLY);
        final RecordingSink cpu = walk(renderLists, renderPass, camera, true);
        GpuCulling.setMode(GpuCullingMode.COMPUTE);
        final RecordingSink compute = walk(renderLists, renderPass, camera, true);

        assertFalse(cpu.ranges.isEmpty(), "face culling removed everything; the fixture is not exercising the path");
        assertTrue(cpu.ranges.size() < 7 || cpu.ranges.get(0)[1] < 28, "face culling did not narrow anything");
        assertEquals(cpu.ranges.size(), compute.ranges.size());
        for (int i = 0; i < cpu.ranges.size(); i++) {
            assertArrayEqualsInt(cpu.ranges.get(i), compute.ranges.get(i), "range " + i);
        }
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual, String label) {
        assertEquals(expected[0], actual[0], label + " vertexOffset");
        assertEquals(expected[1], actual[1], label + " vertexCount");
    }
}
