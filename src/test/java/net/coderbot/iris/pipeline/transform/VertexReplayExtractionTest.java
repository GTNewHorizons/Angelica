package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor.VertexInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexReplayExtractionTest {

    // Shaped like Euphoria Patches' end crystal voxelization in its shadow program
    private static final String CRYSTAL_VERTEX = """
        #version 130
        #extension GL_ARB_shader_image_load_store : enable
        uniform int entityId;
        uniform mat4 shadowModelViewInverse;
        uniform mat4 shadowProjectionInverse;
        attribute vec4 mc_Entity;
        layout(r32i) uniform iimage2D endcrystal_img;
        varying vec2 texCoord;
        void UpdateEndCrystalMap(vec3 pos) {
            int packedPos = int(pos.x + 512.5);
            for (int k = 0; k < 20; k++) {
                int registeredPos = imageAtomicCompSwap(endcrystal_img, ivec2(k, 0), 0, packedPos);
                if (registeredPos == 0 || registeredPos == packedPos) {
                    imageAtomicAdd(endcrystal_img, ivec2(k, 1), int(10.0 * pos.x));
                    break;
                }
            }
        }
        void main() {
            texCoord = gl_MultiTexCoord0.xy;
            vec3 normal = normalize(gl_NormalMatrix * gl_Normal);
            vec4 position = shadowModelViewInverse * shadowProjectionInverse * ftransform();
            if (entityId == 50000 && abs(normal.y) > 0.5 && mc_Entity.x < 0.0) UpdateEndCrystalMap(position.xyz);
            gl_Position = ftransform();
        }
        """;

    private static final String FRAGMENT = """
        #version 130
        uniform sampler2D tex;
        varying vec2 texCoord;
        void main() { gl_FragData[0] = texture2D(tex, texCoord); }
        """;

    private static final InputAvailability TEX_LM = InputAvailability.of(true, true);

    @BeforeAll
    static void initShaderTransformer() throws Exception {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 460);
        ShaderTransformer.init();
    }

    @BeforeEach
    void clearCaches() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
    }

    private static RwImageStoreExtractor.Result replayOf(Map<PatchShaderType, String> transformed) {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay(transformed.get(PatchShaderType.VERTEX));
        assertNotNull(result);
        assertNotNull(result.computeSource(), "every input of an Iris gbuffers vertex shader is replayable");
        return result;
    }

    private static VertexInput input(List<VertexInput> inputs, String name) {
        return inputs.stream().filter(i -> i.name().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError(name + " missing from " + inputs));
    }

    @Test
    void replayKeepsWritesAtomicAndRasterDropsThem() {
        final RwImageStoreExtractor.Result result = replayOf(TransformPatcher.patchAttributes(CRYSTAL_VERTEX, null, FRAGMENT, TEX_LM));

        assertEquals(RwImageStoreExtractor.RwExtractMode.VERTEX_REPLAY, RwImageStoreExtractor.parseSentinel(result.computeSource()));
        assertFalse(result.strippedSource().contains("imageAtomic"), result.strippedSource());
        assertFalse(result.strippedSource().contains("endcrystal_img"), result.strippedSource());
        // Many vertices of one crystal add into the same texels at once
        assertTrue(result.computeSource().contains("imageAtomicAdd"), result.computeSource());
        assertFalse(result.computeSource().contains("_vg_prev"), result.computeSource());
        assertTrue(result.computeSource().contains("layout(binding = 0, r32i) uniform iimage2D endcrystal_img;"), result.computeSource());
        assertEquals(List.of("endcrystal_img"), RwImageStoreExtractor.parseVertexReplayImages(result.computeSource()));
    }

    @Test
    void replayFetchesEveryDeclaredInput() {
        final RwImageStoreExtractor.Result result = replayOf(TransformPatcher.patchAttributes(CRYSTAL_VERTEX, null, FRAGMENT, TEX_LM));
        final List<VertexInput> inputs = RwImageStoreExtractor.parseVertexReplayInputs(result.computeSource());

        assertEquals("vec4", input(inputs, "mc_Entity").glslType());
        for (int k = 0; k < inputs.size(); k++) {
            final VertexInput in = inputs.get(k);
            assertTrue(result.computeSource().contains(in.name() + " = " + in.glslType() + "("), "no fetch for " + in.name());
            assertTrue(result.computeSource().contains("(" + k + "u, _vg_vertex, _vg_i)"), "input " + in.name() + " is not fetched from slot " + k);
        }
    }

    @Test
    void instancedVariantReadsEntityIdFromItsInstanceAttribute() {
        final RwImageStoreExtractor.Result result = replayOf(TransformPatcher.patchAttributesInstanced(CRYSTAL_VERTEX, null, null, null, FRAGMENT, TEX_LM, false, Instancing.TEMPLATE));
        final List<VertexInput> inputs = RwImageStoreExtractor.parseVertexReplayInputs(result.computeSource());

        final VertexInput entity = input(inputs, "iris_Entity");
        assertEquals(InstancedAttribs.LOC_ENTITY, entity.location());
        assertEquals("uvec4", entity.glslType());
        assertTrue(result.computeSource().contains("iris_Entity = uvec4(_vg_uint("), result.computeSource());
        assertFalse(result.computeSource().contains("location"), "a location on a former interface variable does not compile in compute");
    }

    @Test
    void everyDeclaratorOfAnInputListIsFetched() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            in vec3 position, normal;
            layout(r32ui) uniform uimage2D marks;
            void main() {
                imageStore(marks, ivec2(normal.xy), uvec4(1u));
                gl_Position = vec4(position, 1.0);
            }
            """);
        assertNotNull(result.computeSource());
        final List<VertexInput> inputs = RwImageStoreExtractor.parseVertexReplayInputs(result.computeSource());
        assertEquals(List.of("position", "normal"), inputs.stream().map(VertexInput::name).toList());
        assertFalse(result.computeSource().contains("in vec3"), "a stage input does not compile in compute\n" + result.computeSource());
        assertTrue(result.computeSource().contains("normal = vec3(_vg_float(1u, _vg_vertex, _vg_i));"), result.computeSource());
    }

    @Test
    void samplerOnlyTheWriteReadsIsListedForThePipeline() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            in vec4 iris_Vertex;
            uniform sampler2D noisetex;
            layout(rgba8) uniform image2D marks;
            void main() {
                imageStore(marks, ivec2(0), textureLod(noisetex, iris_Vertex.xy, 0.0));
                gl_Position = iris_Vertex;
            }
            """);
        assertFalse(result.strippedSource().contains("textureLod"), result.strippedSource());
        assertEquals(List.of("noisetex"), RwImageStoreExtractor.parseVertexReplaySamplers(result.computeSource()));
    }

    @Test
    void readOnlyImagesBindAfterTheWrittenOnes() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            in vec4 iris_Vertex;
            layout(rgba8) uniform image2D source;
            layout(rgba8) uniform image2D target;
            void main() {
                imageStore(target, ivec2(0), imageLoad(source, ivec2(iris_Vertex.xy)));
                gl_Position = iris_Vertex;
            }
            """);
        final String compute = result.computeSource();
        assertEquals(List.of("target"), RwImageStoreExtractor.parseVertexReplayImages(compute));
        assertEquals(List.of("source"), RwImageStoreExtractor.parseVertexReplayReadImages(compute));
        assertTrue(compute.contains("layout(binding = 0, rgba8) writeonly uniform image2D target;"), compute);
        assertTrue(compute.contains("layout(binding = 1, rgba8) readonly uniform image2D source;"), compute);
        assertEquals(1, compute.split("image2D source", -1).length - 1, compute);
    }

    @Test
    void outParametersOfHelpersStayOutParameters() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            in vec4 iris_Vertex;
            out vec2 texCoord;
            layout(r32f) uniform image2D marks;
            void fill(out float value) { value = iris_Vertex.x; }
            void main() {
                float v = 0.0;
                fill(v);
                imageStore(marks, ivec2(0), vec4(v));
                texCoord = iris_Vertex.xy;
                gl_Position = iris_Vertex;
            }
            """);
        final String compute = result.computeSource().replaceAll("\\s+", " ");
        assertTrue(compute.contains("void fill ( out float value )"), compute);
        assertFalse(compute.contains("out vec2 texCoord"), compute);
    }

    @Test
    void outputBlocksBecomeGlobalsAndLayoutDefaultsStay() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            layout(std430) buffer;
            in vec4 iris_Vertex;
            out gl_PerVertex { vec4 gl_Position; };
            out Outputs { flat int id; vec2 uv; } v;
            out Extra { float fade; };
            buffer Marks { uint counts[]; };
            layout(r32ui) uniform uimage2D marks;
            void main() {
                v.uv = iris_Vertex.xy;
                fade = iris_Vertex.z;
                imageStore(marks, ivec2(v.uv), uvec4(counts[0] + uint(fade)));
                gl_Position = iris_Vertex;
            }
            """);
        assertNotNull(result);
        final String compute = result.computeSource().replaceAll("\\s+", " ");
        assertTrue(compute.contains("layout ( std430 ) buffer ;"), compute);
        assertFalse(compute.contains("gl_PerVertex"), compute);
        assertTrue(compute.contains("struct Outputs { int id ; vec2 uv ; }; Outputs v ;"), compute);
        assertTrue(compute.contains("float fade ;"), compute);
        assertFalse(compute.contains("Extra"), compute);
    }

    @Test
    void primitiveRestartMarkersAreNotReplayedAsVertices() {
        final String compute = replayOf(TransformPatcher.patchAttributes(CRYSTAL_VERTEX, null, FRAGMENT, TEX_LM)).computeSource();
        final int skip = compute.indexOf("if (_vg_restart.x != 0u && _vg_index_value == _vg_restart.y) return;");
        assertTrue(skip >= 0, compute);
        assertTrue(skip < compute.indexOf("_vg_vertex = _vg_index_value + uint(_vg_draw.w);"), compute);
    }

    @Test
    void atomicReplayIsDroppedWithoutBackendSupport() {
        final EnumMap<PatchShaderType, String> atomic = new EnumMap<>(PatchShaderType.class);
        atomic.put(PatchShaderType.VERTEX, TransformPatcher.patchAttributes(CRYSTAL_VERTEX, null, FRAGMENT, TEX_LM).get(PatchShaderType.VERTEX));
        ShaderTransformer.extractRwImageStores(atomic, Patch.ATTRIBUTES, null, false);
        assertFalse(atomic.containsKey(PatchShaderType.COMPUTE));
        assertFalse(atomic.get(PatchShaderType.VERTEX).contains("imageAtomic"), atomic.get(PatchShaderType.VERTEX));

        final EnumMap<PatchShaderType, String> plain = new EnumMap<>(PatchShaderType.class);
        plain.put(PatchShaderType.VERTEX, """
            #version 460 core
            in vec4 iris_Vertex;
            layout(r32ui) uniform uimage2D marks;
            void main() {
                imageStore(marks, ivec2(0), uvec4(1u));
                gl_Position = iris_Vertex;
            }
            """);
        ShaderTransformer.extractRwImageStores(plain, Patch.ATTRIBUTES, null, false);
        assertTrue(plain.containsKey(PatchShaderType.COMPUTE), "plain stores need no atomics, so the replay stays");
    }

    @Test
    void matrixInputIsNotReplayed() {
        final RwImageStoreExtractor.Result result = RwImageStoreExtractor.tryExtractVertexReplay("""
            #version 460 core
            in mat4 instanceMatrix;
            layout(r32ui) uniform uimage2D marks;
            void main() {
                imageStore(marks, ivec2(0), uvec4(1u));
                gl_Position = instanceMatrix[0];
            }
            """);
        assertNotNull(result);
        assertNull(result.computeSource());
        assertFalse(result.strippedSource().contains("imageStore"), result.strippedSource());
    }

    @Test
    void attributesProgramKeepsVertexReplayAndDropsFragmentWrites() {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        result.put(PatchShaderType.VERTEX, """
            #version 460 core
            in vec4 iris_Vertex;
            layout(r32ui) uniform uimage2D marks;
            void main() {
                imageStore(marks, ivec2(0), uvec4(1u));
                gl_Position = iris_Vertex;
            }
            """);
        result.put(PatchShaderType.FRAGMENT, """
            #version 460 core
            layout(r32ui) uniform uimage2D marks;
            out vec4 color;
            void main() {
                imageStore(marks, ivec2(1), uvec4(2u));
                color = vec4(1.0);
            }
            """);

        ShaderTransformer.extractRwImageStores(result, Patch.ATTRIBUTES, null, true);

        assertNotNull(result.get(PatchShaderType.COMPUTE));
        assertFalse(result.get(PatchShaderType.VERTEX).contains("imageStore"));
        assertFalse(result.get(PatchShaderType.FRAGMENT).contains("imageStore"));
    }
}
