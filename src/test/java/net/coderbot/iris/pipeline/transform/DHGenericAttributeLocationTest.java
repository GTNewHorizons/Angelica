package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DHGenericAttributeLocationTest {

    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("vPosition", DHGenericTransformer.DH_GENERIC_LOC_POSITION);
        EXPECTED.put("iris_color", DHGenericTransformer.DH_GENERIC_LOC_COLOR);
        EXPECTED.put("aScale", DHGenericTransformer.DH_GENERIC_LOC_SCALE);
        EXPECTED.put("aTranslateChunk", DHGenericTransformer.DH_GENERIC_LOC_TRANSLATE_CHUNK);
        EXPECTED.put("aTranslateSubChunk", DHGenericTransformer.DH_GENERIC_LOC_TRANSLATE_SUB_CHUNK);
        EXPECTED.put("aMaterial", DHGenericTransformer.DH_GENERIC_LOC_MATERIAL);
    }

    private static final String VERTEX = """
        #version 330 core
        out vec4 col;
        void main() {
            col = vec4(1.0);
            gl_Position = vec4(0.0);
        }
        """;

    private static final String FRAGMENT = """
        #version 330 core
        in vec4 col;
        void main() {
            gl_FragData[0] = col;
        }
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

    private static String patchedVertex() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchDHGeneric("dh_generic_test", VERTEX, null, null, null, FRAGMENT, Object2ObjectMaps.emptyMap());
        assertNotNull(result);
        final String v = result.get(PatchShaderType.VERTEX);
        assertNotNull(v);
        return v;
    }

    @Test
    void everyDhGenericInputCarriesItsAuthoritativeLocation() {
        final String vertex = patchedVertex();

        for (Map.Entry<String, Integer> e : EXPECTED.entrySet()) {
            final Matcher m = Pattern
                .compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s+\\w+\\s+" + e.getKey() + "\\s*;")
                .matcher(vertex);
            assertTrue(m.find(), () -> "no explicit location for '" + e.getKey() + "' in:\n" + vertex);
            assertEquals(e.getValue().intValue(), Integer.parseInt(m.group(1)), () -> e.getKey() + " must sit at DH's hardcoded index");
        }
    }

    @Test
    void noDhGenericInputIsLeftUnlocated() {
        final String vertex = patchedVertex();

        for (String name : EXPECTED.keySet()) {
            final Matcher bare = Pattern.compile("(?m)^\\s*in\\s+\\w+\\s+" + name + "\\s*;").matcher(vertex);
            assertTrue(!bare.find(), () -> "'" + name + "' declared without a location in:\n" + vertex);
        }
    }

    @Test
    void locationsAreABijection() {
        assertEquals(EXPECTED.size(), EXPECTED.values().stream().distinct().count(), "two inputs sharing a location would collide at link time");
        assertEquals(0, EXPECTED.values().stream().min(Integer::compare).orElseThrow().intValue());
        assertEquals(5, EXPECTED.values().stream().max(Integer::compare).orElseThrow().intValue());
    }
}
