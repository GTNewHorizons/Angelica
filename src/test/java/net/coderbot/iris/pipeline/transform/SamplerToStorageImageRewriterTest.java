package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelType;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import net.coderbot.iris.gl.image.ImageInformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerToStorageImageRewriterTest {

    @BeforeEach
    void setUp() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
    }

    @AfterEach
    void tearDown() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of());
    }

    private static Transformer parse(String src) {
        return new Transformer(ShaderParser.parseShader(src).full());
    }

    private static String format(Transformer t) {
        StringBuilder sb = new StringBuilder();
        t.mutateTree(tree -> sb.append(ShaderPrinter.getFormattedShader(tree)));
        return sb.toString();
    }

    @Test
    void integerSampler_texelFetchOnly_rewrittenToImageLoad() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("wsr_sampler", "wsr_img", "r16ui", 3, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform usampler3D wsr_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    uint v = texelFetch(wsr_sampler, ivec3(1,2,3), 0).r;\n" +
            "    fragColor = vec4(float(v));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertFalse(out.contains("usampler3D wsr_sampler"), "old sampler declaration must be gone: " + out);
        assertTrue(out.contains("uimage3D wsr_img"), "image declaration must be injected: " + out);
        assertTrue(out.contains("readonly"), "image must be marked readonly: " + out);
        assertTrue(out.contains("layout(r16ui)"), "layout qualifier must be present: " + out);
        assertTrue(out.contains("imageLoad(wsr_img,"), "texelFetch must be rewritten to imageLoad: " + out);
        assertFalse(out.contains("texelFetch(wsr_img"), "texelFetch must not remain on image: " + out);
        assertFalse(out.contains("wsr_sampler"), "no reference to the old sampler name: " + out);
    }

    @Test
    void filteredRead_abortsRewrite() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("ff_sampler", "ff_img", "r16ui", 3, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform usampler3D ff_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    uvec4 v = texture(ff_sampler, vec3(0.5, 0.5, 0.5));\n" +
            "    fragColor = vec4(float(v.r));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("usampler3D ff_sampler"), "sampler must remain when filtered read present: " + out);
        assertFalse(out.contains("uimage3D"), "no image injected: " + out);
    }

    @Test
    void legacyTexture2DRead_abortsRewrite() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("puddle_sampler", "puddle_img", "r8ui", 2, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform usampler2D puddle_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float noPuddles = texture2D(puddle_sampler, vec2(0.5)).r;\n" +
            "    fragColor = vec4(noPuddles);\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("usampler2D puddle_sampler"), "sampler must remain when read with texture2D: " + out);
        assertFalse(out.contains("puddle_img"), "no image may be injected: " + out);
    }

    @Test
    void legacyTexture3DRead_abortsRewrite() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform usampler3D voxel_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = vec4(float(texture3D(voxel_sampler, vec3(0.5)).r));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("usampler3D voxel_sampler"), "sampler must remain: " + out);
        assertFalse(out.contains("voxel_img"), "no image may be injected: " + out);
    }

    @Test
    void floatSamplerNotInCandidateSet_unchanged() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of());

        String src =
            "#version 460 core\n" +
            "uniform sampler3D ff_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texelFetch(ff_sampler, ivec3(0), 0);\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("sampler3D ff_sampler"), "float sampler must stay sampler: " + out);
        assertFalse(out.contains("uimage3D"), out);
    }

    @Test
    void crossStage_samplerOnlyInFragment_vertexUntouched() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("wsr_sampler", "wsr_img", "r16ui", 3, false)
        ));

        String vs =
            "#version 460 core\n" +
            "void main() { gl_Position = vec4(0.0); }\n";
        String fs =
            "#version 460 core\n" +
            "uniform usampler3D wsr_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    uvec4 v = texelFetch(wsr_sampler, ivec3(0), 0);\n" +
            "    fragColor = vec4(float(v.r));\n" +
            "}\n";

        Transformer vt = parse(vs);
        Transformer ft = parse(fs);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.VERTEX, vt);
        trees.put(PatchShaderType.FRAGMENT, ft);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String fout = format(ft);
        assertFalse(fout.contains("usampler3D wsr_sampler"), fout);
        assertTrue(fout.contains("uimage3D wsr_img"), fout);
        assertTrue(fout.contains("imageLoad(wsr_img,"), fout);

        String vout = format(vt);
        assertFalse(vout.contains("uimage3D"), "vertex must be untouched: " + vout);
        assertFalse(vout.contains("wsr_img"), vout);
    }

    @Test
    void multipleCandidates_sameStage_rewriteAllWithoutAnchorBug() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("wsr_sampler", "wsr_img", "r16ui", 3, false),
            new SamplerToStorageImageRewriter.Candidate("wsr_lod_sampler", "wsr_lod_img", "r8ui", 3, false),
            new SamplerToStorageImageRewriter.Candidate("voxel_sampler", "voxel_img", "r16ui", 3, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform mat4 someMatrix;\n" +
            "uniform usampler3D wsr_sampler;\n" +
            "uniform usampler3D wsr_lod_sampler;\n" +
            "uniform usampler3D voxel_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    uint a = texelFetch(wsr_sampler, ivec3(0), 0).r;\n" +
            "    uint b = texelFetch(wsr_lod_sampler, ivec3(0), 0).r;\n" +
            "    uint c = texelFetch(voxel_sampler, ivec3(0), 0).r;\n" +
            "    fragColor = vec4(float(a + b + c));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertFalse(out.contains("usampler3D"), "no sampler declarations left: " + out);
        assertTrue(out.contains("uimage3D wsr_img"), out);
        assertTrue(out.contains("uimage3D wsr_lod_img"), out);
        assertTrue(out.contains("uimage3D voxel_img"), out);
        assertTrue(out.contains("imageLoad(wsr_img,"), out);
        assertTrue(out.contains("imageLoad(wsr_lod_img,"), out);
        assertTrue(out.contains("imageLoad(voxel_img,"), out);
    }

    @Test
    void imageAlreadyDeclared_skipsRewrite() {
        SamplerToStorageImageRewriter.setActiveCandidates(Set.of(
            new SamplerToStorageImageRewriter.Candidate("wsr_sampler", "wsr_img", "r16ui", 3, false)
        ));

        String src =
            "#version 460 core\n" +
            "uniform usampler3D wsr_sampler;\n" +
            "layout(r16ui) writeonly uniform uimage3D wsr_img;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    uvec4 v = texelFetch(wsr_sampler, ivec3(0), 0);\n" +
            "    imageStore(wsr_img, ivec3(0), v);\n" +
            "    fragColor = vec4(float(v.r));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("usampler3D wsr_sampler"), "sampler decl must remain: " + out);
        assertTrue(out.contains("writeonly"), "writeonly image decl must remain: " + out);
        assertEquals(1, occurrences(out, "uniform uimage3D wsr_img"), "exactly one uimage3D wsr_img declaration (no second readonly injected): " + out);
        assertFalse(out.contains("readonly"), "no readonly decl injected: " + out);
        assertTrue(out.contains("texelFetch(wsr_sampler"), "texelFetch must NOT be rewritten to imageLoad: " + out);
        assertTrue(out.contains("imageStore(wsr_img"), "imageStore on writer must survive: " + out);
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
    }

    @Test
    void noActiveCandidates_noOp() {
        String src =
            "#version 460 core\n" +
            "uniform usampler3D wsr_sampler;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = vec4(float(texelFetch(wsr_sampler, ivec3(0), 0).r)); }\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerToStorageImageRewriter.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("usampler3D wsr_sampler"), "sampler must remain when no candidates: " + out);
        assertFalse(out.contains("uimage3D"), out);
    }

    @Test
    void integerCustomImages_produceNoCandidates_onNonSdlBackend() {
        assertFalse(BackendManager.RENDER_BACKEND.isSDLGPU(), "test backend must not be SDL-GPU for this to mean anything");

        final ImageInformation voxel = new ImageInformation("voxel_img", "voxel_sampler", TextureType.TEXTURE_3D,
            PixelFormat.RED_INTEGER, InternalTextureFormat.R16UI, PixelType.UNSIGNED_INT,
            512, 256, 512, true, false, 0.0f, 0.0f);

        assertTrue(SamplerToStorageImageRewriter.buildCandidates(List.of(voxel)).isEmpty(), "integer custom images must not become storage images off SDL-GPU");
    }
}
