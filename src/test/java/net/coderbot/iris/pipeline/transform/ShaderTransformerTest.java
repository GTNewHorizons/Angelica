package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShaderTransformerTest {

    private static boolean savedImageLoadStore;

    private static void setImageLoadStore(boolean value) throws Exception {
        Reflect.setStatic(RenderSystem.class, "supportsImageLoadStore", value);
    }

    private static boolean getImageLoadStore() throws Exception {
        return Reflect.getStatic(RenderSystem.class, "supportsImageLoadStore");
    }

    @BeforeAll
    static void initShaderTransformer() throws Exception {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 460);

        savedImageLoadStore = getImageLoadStore();
        setImageLoadStore(true);

        ShaderTransformer.clearCache();
        ShaderTransformer.init();
    }

    @AfterAll
    static void restoreCapabilities() throws Exception {
        setImageLoadStore(savedImageLoadStore);
        ShaderTransformer.clearCache();
        TransformPatcher.clearCache();
        ShaderTransformer.init();
    }

    private static int versionOf(String shader) {
        final Matcher m = Pattern.compile("#version\\s+(\\d+)").matcher(shader);
        assertTrue(m.find(), "no #version in\n\n" + shader);
        return Integer.parseInt(m.group(1));
    }

    @Test
    void storageImageRewrite_hoistsEmittedVersion() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)));
        try {
            final String vertex = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }";
            final String fragment = String.join("\n",
                "#version 330 core",
                "uniform usampler3D voxel_sampler;",
                "void main(){ gl_FragColor = vec4(float(texelFetch(voxel_sampler, ivec3(0), 0).r)); }",
                "");

            final Map<PatchShaderType, String> out = TransformPatcher.patchComposite(vertex, null, fragment);
            assertNotNull(out);
            final String fsh = out.get(PatchShaderType.FRAGMENT);
            final String vsh = out.get(PatchShaderType.VERTEX);

            assertTrue(fsh.contains("imageLoad"), "fixture must actually exercise the rewrite\n\n" + fsh);
            assertFalse(fsh.contains("usampler3D voxel_sampler"), fsh);

            assertTrue(versionOf(fsh) >= 420, "injected imageLoad needs GLSL 420\n\n" + fsh);
            assertEquals(330, versionOf(vsh), "the vertex declares no candidate sampler and must not be inflated\n\n" + vsh);
        } finally {
            SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
        }
    }

    @Test
    void filteredRead_doesNotHoistVersion() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)));
        try {
            final String vertex = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }";
            final String fragment = String.join("\n",
                "#version 330 core",
                "uniform usampler3D voxel_sampler;",
                "void main(){ gl_FragColor = vec4(float(texture(voxel_sampler, vec3(0.5)).r)); }",
                "");

            final Map<PatchShaderType, String> out = TransformPatcher.patchComposite(vertex, null, fragment);
            assertNotNull(out);
            final String fsh = out.get(PatchShaderType.FRAGMENT);

            assertTrue(fsh.contains("usampler3D voxel_sampler"), "rewrite must abort on a filtered read\n\n" + fsh);
            assertFalse(fsh.contains("imageLoad"), fsh);
            assertEquals(330, versionOf(fsh), fsh);
        } finally {
            SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
        }
    }

    @Test
    void noCandidates_versionUnchanged() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        final String vertex = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }";
        final String fragment = "#version 330 core\nvoid main(){ gl_FragColor = vec4(1.0); }";

        final Map<PatchShaderType, String> out = TransformPatcher.patchComposite(vertex, null, fragment);
        assertNotNull(out);
        assertTrue(out.get(PatchShaderType.VERTEX).startsWith("#version 330 core\n"), out.get(PatchShaderType.VERTEX));
        assertTrue(out.get(PatchShaderType.FRAGMENT).startsWith("#version 330 core\n"), out.get(PatchShaderType.FRAGMENT));
    }

    @Test
    void computeStorageImageRewrite_hoistsEmittedVersion() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)));
        try {
            final String compute = String.join("\n",
                "#version 330 core",
                "layout(local_size_x = 8) in;",
                "uniform usampler3D voxel_sampler;",
                "out vec4 unused;",
                "void main(){ unused = vec4(float(texelFetch(voxel_sampler, ivec3(gl_GlobalInvocationID), 0).x)); }",
                "");

            final String out = TransformPatcher.patchCompute("shadowcomp", compute, null, null);
            assertNotNull(out);
            assertTrue(out.contains("imageLoad"), "fixture must actually exercise the rewrite\n\n" + out);
            assertTrue(versionOf(out) >= 420, "injected imageLoad needs GLSL 420\n\n" + out);
        } finally {
            SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
        }
    }

    @Test
    void packDeclaredImageTypeHoistsWithoutImageLoad() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        final String vertex = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }";
        final String fragment = String.join("\n",
            "#version 330 core",
            "layout(r16ui) readonly uniform uimage3D voxel_img;",
            "void main(){ gl_FragColor = vec4(float(imageSize(voxel_img).x)); }",
            "");

        final Map<PatchShaderType, String> out = TransformPatcher.patchComposite(vertex, null, fragment);
        assertNotNull(out);
        final String fsh = out.get(PatchShaderType.FRAGMENT);
        assertTrue(versionOf(fsh) >= 420, "uimage3D declaration alone must hoist to 420\n\n" + fsh);
    }

    @Test
    void computeIntegerSamplerBecomesStorageImage() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)));
        try {
            final String compute = String.join("\n",
                "#version 460 core",
                "layout(local_size_x = 8) in;",
                "uniform usampler3D voxel_sampler;",
                "layout(r16ui) writeonly uniform uimage3D floodfill_img;",
                "void main() {",
                "    uint v = texelFetch(voxel_sampler, ivec3(gl_GlobalInvocationID), 0).x;",
                "    imageStore(floodfill_img, ivec3(gl_GlobalInvocationID), uvec4(v, 0u, 0u, 0u));",
                "}",
                "");
            final String out = TransformPatcher.patchCompute("shadowcomp", compute, null, null);
            assertNotNull(out);
            final String norm = out.replaceAll("\\s+", " ");
            assertFalse(norm.contains("usampler3D voxel_sampler"),
                "an integer sampler cannot be bound on SDL_GPU and must become a storage image\n\n" + out);
            assertTrue(norm.contains("uimage3D voxel_img"), "expected the rewritten storage image\n\n" + out);
            assertTrue(norm.contains("imageLoad ( voxel_img"), "texelFetch must become imageLoad\n\n" + out);
        } finally {
            SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
        }
    }

    @Test
    void samplerAliasesSurvive_onNonSdlBackend() {
        assertFalse(BackendManager.RENDER_BACKEND.isSDLGPU(), "test backend must not be SDL-GPU for this to mean anything");
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final String vertex = "#version 330 core\nvoid main(){gl_Position = vec4(0.0);}";
        final String fragment = String.join("\n",
            "#version 330 core",
            "uniform sampler2D gtexture;",
            "in vec2 uv;",
            "void main(){ gl_FragColor = texture2D(gtexture, uv); }",
            "");

        final Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(vertex, null, fragment);
        assertNotNull(transformed);
        final String out = transformed.get(PatchShaderType.FRAGMENT);
        assertNotNull(out);
        assertTrue(out.contains("gtexture"), "the pack's own sampler name must survive off SDL-GPU\n\n" + out);
    }

    @Test
    void transformIsDeterministicUnderConcurrency() throws ExecutionException, InterruptedException {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final String vertex = "#version 120\nvoid main(){gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;}";
        final String fragment = "#version 120\nvoid main(){gl_FragColor = vec4(1.0);}";

        ExecutorService exec = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Map<PatchShaderType, String>>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                tasks.add(() -> TransformPatcher.patchComposite(vertex, null, fragment));
            }

            List<Future<Map<PatchShaderType, String>>> futures = exec.invokeAll(tasks);
            Map<PatchShaderType, String> first = futures.get(0).get();
            assertNotNull(first);

            for (Future<Map<PatchShaderType, String>> f : futures) {
                Map<PatchShaderType, String> r = f.get();
                assertEquals(first, r);
            }
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void formattedShaderIsParseable() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final String vertex = "#version 120\nvoid main(){gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;}";
        final String fragment = "#version 120\nvoid main(){gl_FragColor = vec4(1.0);}";

        Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(vertex, null, fragment);
        assertNotNull(transformed);

        String v = transformed.get(PatchShaderType.VERTEX);
        String f = transformed.get(PatchShaderType.FRAGMENT);
        assertNotNull(v);
        assertNotNull(f);

        ShaderParser.parseShader(v);
        ShaderParser.parseShader(f);
    }
}
