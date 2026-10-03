package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.parameter.ComputeParameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShaderTransformerDiskCacheTest {
    private static final String VERTEX = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }";
    private static final String FRAGMENT = "#version 330 core\nvoid main(){ gl_FragColor = vec4(1.0); }";
    private static final String COMPUTE = String.join("\n",
        "#version 330 core",
        "layout(local_size_x = 8) in;",
        "out vec4 unused;",
        "void main(){ unused = vec4(float(gl_GlobalInvocationID.x)); }",
        "");

    @TempDir
    Path dir;

    private int savedMaxGlsl;

    @BeforeEach
    void setUp() throws Exception {
        savedMaxGlsl = Reflect.getStatic(RenderSystem.class, "maxGlslVersion");
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 460);
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        ShaderTransformer.init();
        ShaderDiskCache.configure(dir, "t");
    }

    @AfterEach
    void tearDown() throws Exception {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        Reflect.setStatic(ShaderDiskCache.class, "root", null);
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", savedMaxGlsl);
        ShaderTransformer.init();
    }

    private static EnumMap<PatchShaderType, String> compositeInputs() {
        final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(PatchShaderType.VERTEX, VERTEX);
        inputs.put(PatchShaderType.GEOMETRY, null);
        inputs.put(PatchShaderType.TESS_CONTROL, null);
        inputs.put(PatchShaderType.TESS_EVAL, null);
        inputs.put(PatchShaderType.FRAGMENT, FRAGMENT);
        return inputs;
    }

    private static TextureStageParameters compositeParams() {
        return new TextureStageParameters(Patch.COMPOSITE, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    @Test
    void compositeDiskHitSkipsTransform() {
        assertNotNull(TransformPatcher.patchComposite(VERTEX, null, FRAGMENT));
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        ShaderDiskCache.putStrings(ShaderTransformer.diskKey(Patch.COMPOSITE, compositeInputs(), compositeParams()), Map.of("VERTEX", "POISON-V", "FRAGMENT", "POISON-F"));

        final Map<PatchShaderType, String> out = TransformPatcher.patchComposite(VERTEX, null, FRAGMENT);
        assertEquals("POISON-V", out.get(PatchShaderType.VERTEX));
        assertEquals("POISON-F", out.get(PatchShaderType.FRAGMENT));
    }

    @Test
    void computeDiskHitSkipsTransform() {
        assertNotNull(TransformPatcher.patchCompute("shadowcomp", COMPUTE, null, null));
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(PatchShaderType.COMPUTE, COMPUTE);
        ShaderDiskCache.putStrings(ShaderTransformer.diskKey(Patch.COMPUTE, inputs, new ComputeParameters(Patch.COMPUTE, null, null)), Map.of("COMPUTE", "POISON-C"));

        assertEquals("POISON-C", TransformPatcher.patchCompute("shadowcomp", COMPUTE, null, null));
    }

    @Test
    void keyChangesWithGlslVersion() throws Exception {
        final String before = ShaderTransformer.diskKey(Patch.COMPOSITE, compositeInputs(), compositeParams()).hex();
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 330);
        final String after = ShaderTransformer.diskKey(Patch.COMPOSITE, compositeInputs(), compositeParams()).hex();
        assertNotEquals(before, after);
    }

    private static Object2ObjectOpenHashMap<Tri<String, TextureType, TextureStage>, String> textureMap(int capacity) {
        final Object2ObjectOpenHashMap<Tri<String, TextureType, TextureStage>, String> m = new Object2ObjectOpenHashMap<>(capacity);
        m.put(new Tri<>("colortex0", TextureType.TEXTURE_2D, TextureStage.COMPOSITE_AND_FINAL), "a.png");
        m.put(new Tri<>("noisetex", TextureType.TEXTURE_3D, TextureStage.GBUFFERS_AND_SHADOW), "b.png");
        m.put(new Tri<>("gaux1", TextureType.TEXTURE_1D, TextureStage.DEFERRED), "c.png");
        return m;
    }

    @Test
    void keyIgnoresTextureMapIterationOrder() {
        final Object2ObjectOpenHashMap<Tri<String, TextureType, TextureStage>, String> m1 = textureMap(16);
        Object2ObjectOpenHashMap<Tri<String, TextureType, TextureStage>, String> m2 = null;
        for (int capacity = 32; capacity <= (1 << 20) && m2 == null; capacity <<= 1) {
            final Object2ObjectOpenHashMap<Tri<String, TextureType, TextureStage>, String> candidate = textureMap(capacity);
            if (!new ArrayList<>(m1.keySet()).equals(new ArrayList<>(candidate.keySet()))) m2 = candidate;
        }
        assertNotNull(m2, "no capacity produced a different iteration order");
        assertNotEquals(new ArrayList<>(m1.keySet()), new ArrayList<>(m2.keySet()));
        assertEquals(m1, m2);

        final String h1 = ShaderTransformer.diskKey(Patch.COMPOSITE, compositeInputs(), new TextureStageParameters(Patch.COMPOSITE, TextureStage.COMPOSITE_AND_FINAL, m1)).hex();
        final String h2 = ShaderTransformer.diskKey(Patch.COMPOSITE, compositeInputs(), new TextureStageParameters(Patch.COMPOSITE, TextureStage.COMPOSITE_AND_FINAL, m2)).hex();
        assertEquals(h1, h2);
    }
}
