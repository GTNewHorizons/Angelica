package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeVertexWriteExtractionTest {

    // Shaped like Euphoria Patches' End prepare pass
    static final String PREPARE_VERTEX = """
        #version 130
        #extension GL_ARB_shader_image_load_store : enable
        uniform float frameTime;
        layout(r32i) uniform iimage2D endcrystal_img;
        varying vec2 texCoord;
        void main() {
            texCoord = gl_MultiTexCoord0.xy;
            gl_Position = ftransform();
            vec4 position0 = ftransform();
            if (position0.x < 0.0 && position0.y > 0.0) {
                for (int index = 0; index < 20; index++) {
                    int state = imageLoad(endcrystal_img, ivec2(index, 8)).r - max(1, int(10000.0 * frameTime));
                    int previousWeight = imageAtomicCompSwap(endcrystal_img, ivec2(index, 8), 0, -1);
                    imageStore(endcrystal_img, ivec2(index, 5), ivec4(state + previousWeight));
                }
            }
        }
        """;

    static final String PREPARE_FRAGMENT = """
        #version 130
        varying vec2 texCoord;
        void main() { gl_FragData[0] = vec4(texCoord, 0.0, 1.0); }
        """;

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

    @Test
    void compositeVertexWritesRunOverTheQuadNotTerrain() {
        final Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(PREPARE_VERTEX, null, PREPARE_FRAGMENT);
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(transformed);
        result.remove(PatchShaderType.COMPUTE);

        ShaderTransformer.extractRwImageStores(result, Patch.COMPOSITE, null, true);

        final String compute = result.get(PatchShaderType.COMPUTE);
        assertEquals(RwImageStoreExtractor.RwExtractMode.COMPOSITE_VSH, RwImageStoreExtractor.parseSentinel(compute));
        assertTrue(compute.contains("iris_Vertex = _vg_quad[vid];"), compute);
        assertTrue(compute.contains("iris_MultiTexCoord0 = _vg_quad[vid];"), compute);
        assertFalse(compute.contains("location"), "a location on a former interface variable does not compile in compute");
        assertEquals(1, compute.split("vec4 iris_ftransform", -1).length - 1, "the transform's own iris_ftransform is the only one");
        assertTrue(compute.contains("#define gl_Position _vg_sink_pos"), compute);
    }

    @Test
    void keptAtomicDropsThePrePassWithoutBackendSupport() {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(TransformPatcher.patchComposite(PREPARE_VERTEX, null, PREPARE_FRAGMENT));
        result.remove(PatchShaderType.COMPUTE);

        // The CompSwap's result is used, so it stays a real atomic, which native Metal cannot bind
        ShaderTransformer.extractRwImageStores(result, Patch.COMPOSITE, null, false);

        assertFalse(result.containsKey(PatchShaderType.COMPUTE));
        assertFalse(result.get(PatchShaderType.VERTEX).contains("imageAtomic"), result.get(PatchShaderType.VERTEX));
    }
}
