package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.config.GpuCullingMode;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.rendering.RenderRegionKeys;
import com.gtnewhorizons.angelica.rendering.celeritas.IndividualDrawBatch;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeBinding;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.array.GlVertexArray;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation;
import org.embeddedt.embeddium.impl.gl.tessellation.TessellationBinding;
import org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.IndirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatchFactory;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class IndirectCullDrawParityTest {

    private static final int FBO_SIZE = 128;
    private static final int GRID = 16;
    private static final int FACINGS = 7;
    private static final int CMD_INTS = 5;

    private static final int SOLID_PASS = 0;
    private static final int CUTOUT_PASS = 1;
    private static final int SORTED_PASS = 2;

    private static final int[] SOLID_MASKS  = { 0x7F, 0x55, 0x2A, 0x63, 0x1C };
    private static final int[] CUTOUT_MASKS = { 0x33, 0x4D, 0x7F };
    private static final int REGION1_SOLID_SECTIONS = 3;

    private static CommandList commandList;
    private static int program;

    private static final class Section {
        int passIndex;
        int sliceMask;
        int slot = -1;
        final int[] quadCount = new int[FACINGS];
        final int[] vertexOffset = new int[FACINGS];
        final int[] elementCount = new int[FACINGS];
        final int[] indexOffset = new int[FACINGS];
        ByteBuffer srd;

        long srdAddress() { return MemoryUtilities.memAddress(srd); }

        void writeSrd() {
            srd.putInt(0, sliceMask);
            srd.putInt(4, 0);
            for (int f = 0; f < FACINGS; f++) {
                final int off = 8 + f * 12;
                srd.putInt(off + 0, vertexOffset[f]);
                srd.putInt(off + 4, elementCount[f]);
                srd.putInt(off + 8, indexOffset[f]);
            }
        }
    }

    private static final class Scene {
        final List<Section> solid = new ArrayList<>();
        final List<Section> cutout = new ArrayList<>();
        final List<Section> sorted = new ArrayList<>();
        FloatBuffer vertices;
        ByteBuffer sharedIndices;
        ByteBuffer sortedIndices;
        int totalQuads;

        List<Section> all() {
            final List<Section> all = new ArrayList<>(solid);
            all.addAll(cutout);
            all.addAll(sorted);
            return all;
        }
    }

    private static final class SceneRig implements AutoCloseable {
        GlMutableBuffer vertexBuffer;
        GlMutableBuffer sharedIndexBuffer;
        GlMutableBuffer sortedIndexBuffer;
        GlVertexArrayTessellation tessNonSorted;
        GlVertexArrayTessellation tessSorted;

        SceneRig(Scene scene) {
            try {
                final ByteBuffer vertexBytes = BufferUtils.createByteBuffer(scene.vertices.capacity() * 4).order(ByteOrder.nativeOrder());
                vertexBytes.asFloatBuffer().put(scene.vertices.duplicate());
                vertexBuffer = upload(vertexBytes);
                sharedIndexBuffer = upload(scene.sharedIndices.duplicate().order(ByteOrder.nativeOrder()));
                sortedIndexBuffer = upload(scene.sortedIndices.duplicate().order(ByteOrder.nativeOrder()));
                tessNonSorted = tessellation(vertexBuffer, sharedIndexBuffer);
                tessSorted = tessellation(vertexBuffer, sortedIndexBuffer);
            } catch (RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        @Override
        public void close() {
            if (tessNonSorted != null) tessNonSorted.delete(commandList);
            if (tessSorted != null) tessSorted.delete(commandList);
            if (vertexBuffer != null) commandList.deleteBuffer(vertexBuffer);
            if (sharedIndexBuffer != null) commandList.deleteBuffer(sharedIndexBuffer);
            if (sortedIndexBuffer != null) commandList.deleteBuffer(sortedIndexBuffer);
        }
    }

    private static final class Fbo implements AutoCloseable {
        int id;
        final int[] textures = new int[2];

        Fbo() {
            try {
                textures[0] = GLStateManager.glGenTextures();
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[0]);
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, FBO_SIZE, FBO_SIZE, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

                textures[1] = GLStateManager.glGenTextures();
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textures[1]);
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, FBO_SIZE, FBO_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                id = GLStateManager.glGenFramebuffers();
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
                GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, textures[0], 0);
                GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[1], 0);
                assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GLStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER), "parity FBO incomplete");
            } catch (RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        @Override
        public void close() {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            if (id != 0) GLStateManager.glDeleteFramebuffers(id);
            for (int tex : textures) {
                if (tex != 0) GLStateManager.glDeleteTextures(tex);
            }
        }
    }

    @BeforeAll
    static void setUpDevice() {
        GLRenderDevice.VANILLA_STATE_RESETTER = () -> {};
        RenderDevice.enterManagedCode();
        commandList = RenderDevice.INSTANCE.createCommandList();
        program = buildProgram();
    }

    @AfterAll
    static void tearDownDevice() {
        if (program != 0) GLStateManager.glDeleteProgram(program);
        RenderDevice.exitManagedCode();
    }

    @AfterEach
    void resetGlState() {
        GLStateManager.glUseProgram(0);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glViewport(0, 0, 800, 600);
    }

    static final String FLAT_VS = "#version 330 core\n"
        + "layout(location = 0) in vec3 a_Pos;\n"
        + "void main() { gl_Position = vec4(a_Pos, 1.0); }\n";
    static final String FLAT_FS = "#version 330 core\n"
        + "out vec4 fragColor;\n"
        + "void main() { fragColor = vec4(1.0); }\n";

    static int compileShader(int type, String src) {
        final int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, src);
        GLStateManager.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), () -> "shader failed to compile:\n" + GLStateManager.glGetShaderInfoLog(shader, 4096));
        return shader;
    }

    private static int buildProgram() {
        final int vsh = compileShader(GL20.GL_VERTEX_SHADER, FLAT_VS);
        final int fsh = compileShader(GL20.GL_FRAGMENT_SHADER, FLAT_FS);
        final int prog = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(prog, vsh);
        GLStateManager.glAttachShader(prog, fsh);
        GLStateManager.glLinkProgram(prog);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS), () -> "parity program failed to link:\n" + GLStateManager.glGetProgramInfoLog(prog, 4096));
        GLStateManager.glDeleteShader(vsh);
        GLStateManager.glDeleteShader(fsh);
        return prog;
    }

    private static Scene buildScene() {
        final Scene scene = new Scene();
        for (int s = 0; s < SOLID_MASKS.length; s++) {
            scene.solid.add(newSection(SOLID_PASS, SOLID_MASKS[s], s));
        }
        for (int s = 0; s < CUTOUT_MASKS.length; s++) {
            scene.cutout.add(newSection(CUTOUT_PASS, CUTOUT_MASKS[s], SOLID_MASKS.length + s));
        }
        for (int s = 0; s < SOLID_MASKS.length; s++) {
            scene.sorted.add(newSection(SORTED_PASS, SOLID_MASKS[s], s + 1));
        }

        int quad = 0;
        int vertex = 0;
        int maxQuadsPerDraw = 0;
        int sortedIndexInts = 0;
        final List<float[]> quadRects = new ArrayList<>();
        for (Section section : allOf(scene)) {
            final int sectionFirstVertex = vertex;
            for (int f = 0; f < FACINGS; f++) {
                section.vertexOffset[f] = vertex;
                section.elementCount[f] = 6 * section.quadCount[f];
                section.indexOffset[f] = section.passIndex == SORTED_PASS ? sortedIndexInts * 4 : 0;
                if (section.passIndex == SORTED_PASS) sortedIndexInts += section.elementCount[f];
                for (int q = 0; q < section.quadCount[f]; q++) {
                    quadRects.add(quadRect(quad));
                    quad++;
                    vertex += 4;
                }
            }
            maxQuadsPerDraw = Math.max(maxQuadsPerDraw, (vertex - sectionFirstVertex) / 4);
            section.writeSrd();
        }
        scene.totalQuads = quad;
        assertTrue(quad <= GRID * GRID, "scene overflows the NDC cell grid");

        scene.vertices = BufferUtils.createFloatBuffer(vertex * 3);
        for (float[] rect : quadRects) {
            final float x0 = rect[0], y0 = rect[1], x1 = rect[2], y1 = rect[3], z = rect[4];
            scene.vertices.put(x0).put(y0).put(z);
            scene.vertices.put(x1).put(y0).put(z);
            scene.vertices.put(x1).put(y1).put(z);
            scene.vertices.put(x0).put(y1).put(z);
        }
        scene.vertices.flip();

        scene.sharedIndices = BufferUtils.createByteBuffer(6 * maxQuadsPerDraw * 4).order(ByteOrder.nativeOrder());
        for (int q = 0; q < maxQuadsPerDraw; q++) {
            putQuadIndices(scene.sharedIndices, 4 * q);
        }
        scene.sharedIndices.flip();

        scene.sortedIndices = BufferUtils.createByteBuffer(sortedIndexInts * 4).order(ByteOrder.nativeOrder());
        for (Section section : scene.sorted) {
            for (int f = 0; f < FACINGS; f++) {
                if ((section.sliceMask & (1 << f)) == 0) continue;
                for (int q = 0; q < section.quadCount[f]; q++) {
                    putQuadIndices(scene.sortedIndices, 4 * q);
                }
            }
        }
        scene.sortedIndices.flip();
        return scene;
    }

    private static List<Section> allOf(Scene scene) {
        return scene.all();
    }

    private static Section newSection(int passIndex, int sliceMask, int seed) {
        final Section section = new Section();
        section.passIndex = passIndex;
        section.sliceMask = sliceMask;
        for (int f = 0; f < FACINGS; f++) {
            if ((sliceMask & (1 << f)) != 0) {
                section.quadCount[f] = 1 + ((seed + f) % 3);
            }
        }
        section.srd = MemoryUtilities.memAlloc(92).order(ByteOrder.nativeOrder());
        return section;
    }

    private static float[] quadRect(int quadIndex) {
        final int cx = quadIndex % GRID;
        final int cy = quadIndex / GRID;
        final float cell = 2.0f / GRID;
        final float x0 = -1.0f + cx * cell + cell * 0.1f;
        final float y0 = -1.0f + cy * cell + cell * 0.1f;
        final float z = -0.9f + 1.8f * quadIndex / (GRID * GRID);
        return new float[] { x0, y0, x0 + cell * 0.8f, y0 + cell * 0.8f, z };
    }

    private static void putQuadIndices(ByteBuffer out, int base) {
        out.putInt(base).putInt(base + 1).putInt(base + 2);
        out.putInt(base + 2).putInt(base + 3).putInt(base);
    }

    private static GlMutableBuffer upload(ByteBuffer data) {
        final GlMutableBuffer buffer = commandList.createMutableBuffer();
        commandList.uploadData(buffer, data, GlBufferUsage.STATIC_DRAW);
        return buffer;
    }

    private static GlVertexArrayTessellation tessellation(GlMutableBuffer vertexBuffer, GlMutableBuffer indexBuffer) {
        final GlVertexAttribute position = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a_Pos", 3, false, 0, 12, false);
        final TessellationBinding[] bindings = {
            TessellationBinding.forVertexBuffer(vertexBuffer, new GlVertexAttributeBinding[] { new GlVertexAttributeBinding(0, position) }),
            TessellationBinding.forElementBuffer(indexBuffer),
        };
        final GlVertexArrayTessellation tess = new GlVertexArrayTessellation(new GlVertexArray(), bindings);
        tess.init(commandList);
        return tess;
    }

    private static void beginDrawing(int fbo) {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_LESS);
        GLStateManager.glDepthMask(true);
        GLStateManager.disableCull();
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearColor(0f, 0f, 0f, 0f);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GLStateManager.glUseProgram(program);
    }

    private static FloatBuffer readDepth(int fbo) {
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        final FloatBuffer depth = BufferUtils.createFloatBuffer(FBO_SIZE * FBO_SIZE);
        GLStateManager.glReadPixels(0, 0, FBO_SIZE, FBO_SIZE, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        return depth;
    }

    private static int coveredPixels(FloatBuffer depth) {
        int covered = 0;
        for (int i = 0; i < depth.capacity(); i++) {
            if (depth.get(i) < 1.0f) covered++;
        }
        return covered;
    }

    private static void assertDepthParity(int directFbo, int otherFbo, String label) {
        final FloatBuffer direct = readDepth(directFbo);
        final FloatBuffer other = readDepth(otherFbo);
        final int covered = coveredPixels(direct);
        assertTrue(covered > 100, label + ": direct path drew almost nothing (" + covered + " covered pixels); scene setup is broken");
        assertEquals(covered, coveredPixels(other), label + ": covered pixel count differs between direct and the compared path");
        for (int i = 0; i < direct.capacity(); i++) {
            if (direct.get(i) != other.get(i)) {
                final int x = i % FBO_SIZE;
                final int y = i / FBO_SIZE;
                assertEquals(direct.get(i), other.get(i), label + ": depth mismatch at (" + x + "," + y + "); the compared path rendered different geometry than the direct path");
            }
        }
    }

    private static void setFrustumUbo(GpuTerrainCuller culler, ByteBuffer ubo) {
        try {
            final Field field = GpuTerrainCuller.class.getDeclaredField("frustumUboBytes");
            field.setAccessible(true);
            field.set(culler, ubo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void executeRegion(GpuTerrainCuller culler, RenderRegion region, GlVertexArrayTessellation tessellation) {
        final MultiDrawBatch batch = culler.batchForRegion(region);
        if (batch != null) {
            batch.execute(commandList, tessellation, GlPrimitiveType.TRIANGLES);
        }
    }

    private static int reflectInt(GpuTerrainCuller emitter, String fieldName) {
        try {
            final Field field = GpuTerrainCuller.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(emitter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ByteBuffer frustumUbo(int indexPointerMask) {
        final ByteBuffer ubo = FrustumExtractor.allocateUboByteBuffer();
        final Matrix4f identity = new Matrix4f();
        FrustumExtractor.writeStd140(identity, 0, indexPointerMask, ubo);
        FrustumExtractor.patchCameraWorld(0f, 0f, 0f, ubo);
        FrustumExtractor.patchBypassFrustum(true, ubo);
        FrustumExtractor.patchPrimitiveRatio(QuadPrimitiveType.TRIANGULATED.getVerticesPerPrimitive(), QuadPrimitiveType.TRIANGULATED.getIndexBufferElementsPerPrimitive(), ubo);
        return ubo;
    }

    private static void metaUpdate(SectionMetaBuffer meta, Section section, int localSectionIndex) {
        section.slot = meta.update(section.passIndex, 0, 0, 0, localSectionIndex, section.srdAddress(), section.sliceMask, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
        assertTrue(section.slot >= 0, "meta rejected section for pass " + section.passIndex + " local index " + localSectionIndex);
    }

    private static void pushSectionCommands(MultiDrawBatch batch, long srdAddress, int sliceMask, int indexPointerMask) {
        for (int facing = 0; facing < FACINGS; facing++) {
            if (((sliceMask >> facing) & 1) == 0) {
                continue;
            }

            batch.appendDrawCommand(SectionRenderDataUnsafe.Strategy.FULL.getVertexOffset(srdAddress, facing),
                    SectionRenderDataUnsafe.Strategy.FULL.getElementCount(srdAddress, facing, QuadPrimitiveType.TRIANGULATED),
                    SectionRenderDataUnsafe.Strategy.FULL.getIndexOffset(srdAddress, facing) & indexPointerMask);
        }
    }

    private static void renderCpuBatches(Scene scene, int fbo, GlVertexArrayTessellation tessNonSorted, GlVertexArrayTessellation tessSorted, MultiDrawBatchFactory factory) {
        beginDrawing(fbo);

        renderCpuPass(factory, tessNonSorted, 0, List.of(
                scene.solid.subList(0, REGION1_SOLID_SECTIONS),
                scene.solid.subList(REGION1_SOLID_SECTIONS, scene.solid.size())));
        renderCpuPass(factory, tessNonSorted, 0, List.of(scene.cutout));
        renderCpuPass(factory, tessSorted, 0xFFFFFFFF, List.of(scene.sorted));
    }

    private static void renderCpuPass(MultiDrawBatchFactory factory, GlVertexArrayTessellation tessellation, int indexPointerMask, List<List<Section>> regions) {
        for (List<Section> region : regions) {
            final MultiDrawBatch batch = factory.create(MultiDrawBatch.MAX_COMMAND_COUNT);
            try {
                for (Section section : region) {
                    pushSectionCommands(batch, section.srdAddress(), section.sliceMask, indexPointerMask);
                }
                batch.upload(commandList);
                batch.execute(commandList, tessellation, GlPrimitiveType.TRIANGLES);
            } finally {
                batch.delete();
            }
        }
    }

    private static void mutateFirstSolidSection(Scene scene) {
        final Section mutated = scene.solid.get(0);
        int shrinkFacing = -1;
        for (int f = 0; f < FACINGS; f++) {
            if ((mutated.sliceMask & (1 << f)) != 0 && mutated.quadCount[f] > 1) { shrinkFacing = f; break; }
        }
        assertTrue(shrinkFacing >= 0, "scene has no shrinkable facing");
        mutated.quadCount[shrinkFacing]--;
        mutated.elementCount[shrinkFacing] -= 6;

        for (int f = shrinkFacing + 1; f < FACINGS; f++) {
            mutated.vertexOffset[f] -= 4;
            if (mutated.passIndex == SORTED_PASS) mutated.indexOffset[f] -= 6 * 4;
        }
        mutated.writeSrd();
    }

    private static final class GpuFramePasses {
        final List<int[]> expectedCombined = new ArrayList<>();
        final List<int[]> expectedSorted = new ArrayList<>();
        int combinedIndirectBuffer;
        int sortedIndirectBuffer;
    }

    private static GpuFramePasses renderGpu(Scene scene, int fbo, GlVertexArrayTessellation tessNonSorted, GlVertexArrayTessellation tessSorted, GpuTerrainCuller gpu, RenderRegion region1, RenderRegion region2, String frameLabel) {
        final GpuFramePasses result = new GpuFramePasses();
        beginDrawing(fbo);

        gpu.beginCombinedPasses(0, 0);
        assertTrue(gpu.isComputeActiveThisPass(), "compute path unexpectedly inactive");
        setFrustumUbo(gpu, frustumUbo(0));
        gpu.syncSectionMetaIfDirty();

        int outputBase = 0;
        outputBase = walkSections(scene.solid.subList(0, REGION1_SOLID_SECTIONS), gpu, region1, outputBase, result.expectedCombined, 0);
        outputBase = walkSections(scene.solid.subList(REGION1_SOLID_SECTIONS, scene.solid.size()), gpu, region2, outputBase, result.expectedCombined, 0);
        gpu.startSecondPass();
        walkSections(scene.cutout, gpu, region1, outputBase, result.expectedCombined, 0);
        gpu.finishCombinedBuild();

        executeRegion(gpu, region1, tessNonSorted);
        executeRegion(gpu, region2, tessNonSorted);
        gpu.endPass();

        assertTrue(gpu.selectPreparedSecondPass(), "prepared cutout pass was not selectable");
        executeRegion(gpu, region1, tessNonSorted);
        gpu.endPass();
        result.combinedIndirectBuffer = reflectInt(gpu, "indirectSsboGlId");
        assertIndirectBufferMatches(result.combinedIndirectBuffer, result.expectedCombined, frameLabel + " combined solid+cutout pass");

        gpu.beginCullPass(0xFFFFFFFF);
        setFrustumUbo(gpu, frustumUbo(0xFFFFFFFF));
        gpu.syncSectionMetaIfDirty();
        walkSections(scene.sorted, gpu, region1, 0, result.expectedSorted, 0xFFFFFFFF);
        executeRegion(gpu, region1, tessSorted);
        gpu.endPass();
        result.sortedIndirectBuffer = reflectInt(gpu, "indirectSsboGlId");
        assertIndirectBufferMatches(result.sortedIndirectBuffer, result.expectedSorted, frameLabel + " sorted pass");

        return result;
    }

    private static int walkSections(List<Section> sections, GpuTerrainCuller gpu, RenderRegion region, int outputBase, List<int[]> expectedCommands, int indexPointerMask) {
        gpu.reserveSections(sections.size());
        final int drawStart = outputBase;
        int drawCount = 0;
        int maxElems = 0;
        for (int i = 0; i < sections.size(); i++) {
            final Section section = sections.get(i);
            gpu.appendSection(section.slot, section.sliceMask, outputBase);

            final boolean mergeRuns = indexPointerMask == 0;
            final long runs = mergeRuns ? BatchAssembler.packRuns(section.sliceMask & 0x7F) : 0L;
            final int runCount = mergeRuns ? BatchAssembler.runCount(runs) : Integer.bitCount(section.sliceMask & 0x7F);
            final long srd = section.srdAddress();
            final SectionRenderDataUnsafe.Strategy layout = SectionRenderDataUnsafe.Strategy.FULL;
            final int sectionBase = layout.getVertexOffset(srd, 0);
            final int indexBase = layout.getIndexOffset(srd, 0) / 4;
            int singleFacing = -1;
            for (int run = 0; run < runCount; run++) {
                if (!mergeRuns) {
                    do { singleFacing++; } while ((section.sliceMask & (1 << singleFacing)) == 0);
                }
                final int firstFacing = mergeRuns ? BatchAssembler.runFirst(runs, run) : singleFacing;
                final int lastFacing = mergeRuns ? BatchAssembler.runLast(runs, run) : singleFacing;
                final int startVertex = layout.getVertexOffset(srd, firstFacing);
                final int endVertex = layout.getRunVertexEnd(srd, lastFacing, QuadPrimitiveType.TRIANGULATED);
                final int elements = SectionRenderDataUnsafe.elementsForVertices(endVertex - startVertex, QuadPrimitiveType.TRIANGULATED);
                final int firstIndex = indexBase
                    + SectionRenderDataUnsafe.elementsForVertices(startVertex - sectionBase, QuadPrimitiveType.TRIANGULATED);
                expectedCommands.add(new int[] { elements, 1, firstIndex & indexPointerMask, startVertex, 0 });
                outputBase++;
                drawCount++;
                maxElems = Math.max(maxElems, elements);
            }
        }
        if (drawCount > 0) {
            gpu.recordRegion(region, drawStart, drawCount, maxElems);
        }
        return outputBase;
    }

    private static void assertIndirectBufferMatches(int bufferId, List<int[]> expected, String label) {
        assertNotEquals(0, bufferId, label + ": no indirect buffer was created");
        final ByteBuffer readback = BufferUtils.createByteBuffer(expected.size() * CMD_INTS * 4).order(ByteOrder.nativeOrder());
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId);
        GLStateManager.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, readback);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        final String[] fieldNames = { "count", "instanceCount", "firstIndex", "baseVertex", "baseInstance" };
        for (int c = 0; c < expected.size(); c++) {
            for (int i = 0; i < CMD_INTS; i++) {
                assertEquals(expected.get(c)[i], readback.getInt((c * CMD_INTS + i) * 4), label + ": command " + c + " field " + fieldNames[i] + " differs from the CPU-computed command stream");
            }
        }
    }

    private static void runCpuFactoryParity(MultiDrawBatchFactory other, String label) {
        final Scene scene = buildScene();
        final MultiDrawBatchFactory direct = DirectMultiDrawBatch::new;

        try {
            try (SceneRig rig = new SceneRig(scene);
                 Fbo directFbo = new Fbo();
                 Fbo otherFbo = new Fbo()) {

                for (int frame = 1; frame <= 2; frame++) {
                    renderCpuBatches(scene, directFbo.id, rig.tessNonSorted, rig.tessSorted, direct);
                    renderCpuBatches(scene, otherFbo.id, rig.tessNonSorted, rig.tessSorted, other);
                    assertDepthParity(directFbo.id, otherFbo.id, "frame " + frame + " (" + label + ")");
                }

                mutateFirstSolidSection(scene);

                renderCpuBatches(scene, directFbo.id, rig.tessNonSorted, rig.tessSorted, direct);
                renderCpuBatches(scene, otherFbo.id, rig.tessNonSorted, rig.tessSorted, other);
                assertDepthParity(directFbo.id, otherFbo.id, "frame 3 (" + label + ")");
            }
        } finally {
            for (Section section : scene.all()) MemoryUtilities.memFree(section.srd);
        }
    }

    @Test
    void cpuIndirectBatchMatchesDirectAcrossFrames() {
        assumeTrue(RenderSystem.supportsMultiDrawIndirect(), "multi-draw-indirect unsupported");
        runCpuFactoryParity(IndirectMultiDrawBatch::new, "celeritas CPU indirect emitter");
    }

    @Test
    void individualDrawBatchMatchesDirectAcrossFrames() {
        runCpuFactoryParity(IndividualDrawBatch::new, "individual draw batch");
    }

    @Test
    void gpuCulledIndirectMatchesDirectAcrossFrames() {
        assumeTrue(RenderSystem.supportsCompute() && RenderSystem.supportsMultiDrawIndirect(),
            "compute-driven indirect culling unsupported");

        GpuCulling.setMode(GpuCullingMode.COMPUTE);
        try {
            final Scene scene = buildScene();
            final GpuDrivenChunkCuller culler = new GpuDrivenChunkCuller();
            final SectionMetaBuffer meta = new SectionMetaBuffer();
            final GpuTerrainCuller gpu;
            try {
                assertTrue(culler.ensureReady(), "chunk_cull.csh failed to load on a real driver");
                gpu = new GpuTerrainCuller(culler, meta);
            } catch (RuntimeException | Error e) {
                meta.shutdown();
                culler.shutdown();
                throw e;
            }

            final RenderRegion region1 = RenderRegionKeys.create(0, 0, 0, 0);
            final RenderRegion region2 = RenderRegionKeys.create(0, 0, 0, 1);
            final MultiDrawBatchFactory direct = DirectMultiDrawBatch::new;

            try {
                try (SceneRig rig = new SceneRig(scene);
                     Fbo directFbo = new Fbo();
                     Fbo gpuFbo = new Fbo()) {

                    int local = 0;
                    for (Section section : scene.solid) metaUpdate(meta, section, local++);
                    local = 0;
                    for (Section section : scene.cutout) metaUpdate(meta, section, local++);
                    local = 0;
                    for (Section section : scene.sorted) metaUpdate(meta, section, local++);

                    for (int frame = 1; frame <= 2; frame++) {
                        renderCpuBatches(scene, directFbo.id, rig.tessNonSorted, rig.tessSorted, direct);
                        renderGpu(scene, gpuFbo.id, rig.tessNonSorted, rig.tessSorted, gpu, region1, region2, "frame " + frame);
                        assertDepthParity(directFbo.id, gpuFbo.id, "frame " + frame + " (GPU-culled indirect emitter)");
                    }

                    mutateFirstSolidSection(scene);
                    metaUpdate(meta, scene.solid.get(0), 0);

                    renderCpuBatches(scene, directFbo.id, rig.tessNonSorted, rig.tessSorted, direct);
                    renderGpu(scene, gpuFbo.id, rig.tessNonSorted, rig.tessSorted, gpu, region1, region2, "frame 3 (after section mesh update)");
                    assertDepthParity(directFbo.id, gpuFbo.id, "frame 3 (GPU-culled indirect emitter)");
                }
            } finally {
                gpu.delete();
                meta.shutdown();
                culler.shutdown();
                for (Section section : scene.all()) MemoryUtilities.memFree(section.srd);
            }
        } finally {
            GpuCulling.setMode(GpuCullingMode.CPU_ONLY);
        }
    }
}
