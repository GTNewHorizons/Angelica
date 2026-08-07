package net.coderbot.iris.pipeline.transform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RwImageStoreExtractorTest {

    @AfterEach
    void clearImageRegistry() {
        RwImageStoreExtractor.setActiveCustomImages(null);
    }

    private static String norm(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static final String MINIMAL_VOX_VSH = String.join("\n",
        "#version 460 core",
        "#extension GL_ARB_shader_image_load_store : enable",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "attribute vec4 mc_Entity;",
        "attribute vec4 at_midBlock;",
        "writeonly uniform uimage3D voxel_img;",
        "out vec2 texCoord;",
        "flat out int mat;",
        "uniform mat4 gbufferModelView;",
        "void main() {",
        "    texCoord = iris_MultiTexCoord0.xy;",
        "    mat = int(mc_Entity.x + 0.5);",
        "    gl_Position = vec4(iris_Vertex.xyz, 1.0);",
        "    if (gl_VertexID % 4 == 0) {",
        "        imageStore(voxel_img, ivec3(at_midBlock.xyz), uvec4(uint(mat), 0u, 0u, 0u));",
        "    }",
        "}",
        "");

    private static final String NO_IMAGESTORE_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "void main() { gl_Position = iris_Vertex; }",
        "");

    @Test void detection_returnsNullWhenNoImageStore() {
        assertNull(RwImageStoreExtractor.tryExtract(NO_IMAGESTORE_VSH, PatchShaderType.VERTEX, "shadow"));
    }

    @Test void detection_returnsNullOnNullInput() {
        assertNull(RwImageStoreExtractor.tryExtract(null, PatchShaderType.VERTEX, "shadow"));
    }

    @Test void writtenImages_collectedFromImageStoreCalls() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        assertNotNull(result);
        assertTrue(result.writtenImages().contains("voxel_img"));
    }

    @Test void modeIsChunkWhenChunkAttrsPresent() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        assertEquals(RwImageStoreExtractor.RwExtractMode.CHUNK, result.mode());
    }

    @Test void compute_hasComputeVersionAndWorkgroup() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        assertTrue(result.computeSource().startsWith("// _vg_mode: chunk\n"));
        assertTrue(result.computeSource().contains("#version 460 core"));
        assertTrue(result.computeSource().contains("layout(local_size_x = 64) in;"));
    }

    @Test void compute_hasUniformsAndSsboBinding() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        final String s = result.computeSource();
        assertTrue(s.contains("uniform int _vg_startVertex;"));
        assertTrue(s.contains("uniform int _vg_vertexCount;"));
        assertTrue(s.contains("readonly buffer _VgVbuf"));
        assertTrue(s.contains("layout(std430, binding = " + RwImageStoreExtractor.VG_VBUF_SSBO_BINDING + ") readonly buffer _VgVbuf"), "VBO SSBO must bind at the dedicated voxelization slot (not 0..8 which shaderpacks claim via buffer.N)");
    }

    @Test void compute_ssboBindingDoesNotCollideWithShaderpackSlots() {
        assertTrue(RwImageStoreExtractor.VG_VBUF_SSBO_BINDING >= 9, "VG_VBUF_SSBO_BINDING must be >= 9 to avoid colliding with shaderpack buffer.N slots; got " + RwImageStoreExtractor.VG_VBUF_SSBO_BINDING);
    }

    @Test void compute_renamesOriginalMainToVgBody() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        final String s = result.computeSource();
        assertTrue(s.contains("_vg_body"), "expected _vg_body in compute output");
        assertFalse(norm(s).contains("void main ( ) { texCoord ="), "user-original main must be renamed");
    }

    @Test void compute_synthesizedMainCallsUnpackAndBody() {
        final String s = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow").computeSource();
        assertTrue(s.contains("gl_GlobalInvocationID"));
        assertTrue(s.contains("_vg_unpack(uint(id));"));
        assertTrue(s.contains("_vg_body();"));
    }

    @Test void compute_hoistsAttributeDependentGlobalInitializers() {
        final String vsh = MINIMAL_VOX_VSH.replace(
            "void main() {",
            "float tAmin = fract(mc_Entity.y);\nvec4 iris_Entity = vec4(mc_Entity.x - 1.0, 0.0, 0.0, 1.0);\nvoid main() {");
        final String s = RwImageStoreExtractor.tryExtract(vsh, PatchShaderType.VERTEX, "shadow").computeSource();
        final String n = norm(s);
        assertFalse(n.contains("vec4 iris_Entity = "), "global initializers evaluate before the dispatch main decodes attributes; must be stripped");
        assertTrue(n.contains("iris_Entity = vec4 ( mc_Entity . x"), "every stripped initializer must be re-emitted as an assignment, not just the first");
        assertTrue(n.contains("tAmin = fract ( mc_Entity . y"), "every stripped initializer must be re-emitted as an assignment, not just the first");
        assertTrue(n.indexOf("_vg_body ( ) {") < n.indexOf("iris_Entity = vec4 ( mc_Entity . x"), "the assignment must run inside the body, after the attribute decode");
        assertTrue(n.indexOf("tAmin = fract") < n.indexOf("iris_Entity = vec4"), "hoisted assignments must keep declaration order");
    }

    @Test void compute_keepsUnsizedArrayInitializersInPlace() {
        final String vsh = MINIMAL_VOX_VSH.replace(
            "void main() {",
            "vec3[] tintA = vec3[](vec3(1.0), vec3(0.5));\nvec3 tintB[] = vec3[](vec3(1.0), vec3(0.5));\nvoid main() {");
        final String s = RwImageStoreExtractor.tryExtract(vsh, PatchShaderType.VERTEX, "shadow").computeSource();
        final String n = norm(s);
        assertTrue(n.contains("tintA = vec3 [ ] ("), "tintA: unsized array initializer must stay on the declaration");
        assertTrue(n.contains("tintB [ ] = vec3 [ ] ("), "tintB: unsized array initializer must stay on the declaration");
        assertTrue(n.indexOf("tintA =") == n.lastIndexOf("tintA ="), "tintA: initializer must not additionally be hoisted into the body");
    }

    @Test void compute_unpackHasExpectedAttributeReads() {
        final String s = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow").computeSource();
        assertTrue(s.contains("iris_Vertex = vec4(uintBitsToFloat(_vg_vbuf.data[base + 0u])"));
        assertTrue(s.contains("iris_Color = vec4(float(_vg_vbuf.data[base + 3u] & 0xFFu)"));
        assertTrue(s.contains("mc_Entity = vec4(float(_vg_vbuf.data[base + 10u])"), "ATTRIBUTES-patch mc_Entity is delivered as vec4 raw");
    }

    @Test void compute_dropsOutQualifierFromVaryings() {
        final String s = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow").computeSource();
        assertFalse(norm(s).contains("out vec2 texCoord"), "out qualifier should be stripped\n\n" + s);
        assertTrue(norm(s).contains("vec2 texCoord"), "varying becomes a global");
    }

    @Test void compute_vertInitOnlyAssignsUnpackedAttributes() {
        final String src = String.join("\n",
            "#version 460 core",
            "in vec3 a_PosId;",
            "writeonly uniform uimage3D voxel_img;",
            "void main() {",
            "    gl_Position = vec4(a_PosId, 1.0);",
            "    imageStore(voxel_img, ivec3(a_PosId), uvec4(1u, 0u, 0u, 0u));",
            "}",
            "");
        final String s = norm(RwImageStoreExtractor.tryExtract(src, PatchShaderType.VERTEX, "shadow").computeSource());
        assertTrue(s.contains("_vert_position = a_PosId"), "the used attribute is still decoded\n\n" + s);
        assertFalse(s.contains("a_TexCoord"), "an unused attribute must not be referenced\n\n" + s);
        assertFalse(s.contains("a_LightCoord"), "an unused attribute must not be referenced\n\n" + s);
    }

    @Test void compute_dropsInterpolationQualifierFromVaryings() {
        final String s = norm(RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow").computeSource());
        assertFalse(s.contains("flat"), "flat is interface-only; a compute global carrying it does not compile\n\n" + s);
        assertTrue(s.contains("int mat"), "the varying still becomes a global");
    }

    @Test void compute_dropsAuxiliaryStorageQualifierFromVaryings() {
        final String src = String.join("\n",
            "#version 460 core",
            "in vec4 iris_Vertex;",
            "writeonly uniform uimage3D voxel_img;",
            "centroid out vec2 texCoord;",
            "void main() {",
            "    texCoord = iris_Vertex.xy;",
            "    imageStore(voxel_img, ivec3(0), uvec4(1u, 0u, 0u, 0u));",
            "}",
            "");
        final String s = norm(RwImageStoreExtractor.tryExtract(src, PatchShaderType.VERTEX, "shadow").computeSource());
        assertFalse(s.contains("centroid"), "centroid is interface-only\n\n" + s);
        assertTrue(s.contains("vec2 texCoord"), "the varying still becomes a global");
    }

    @Test void compute_stripsChunkVertexAttributeDeclarations() {
        final String s = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow").computeSource();
        assertFalse(norm(s).contains("in vec4 iris_Vertex"));
        assertFalse(norm(s).contains("attribute vec4 mc_Entity"));
    }

    @Test void raster_strippedHasNoImageStoreOrWriteonly() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        final String r = result.strippedSource();
        assertFalse(r.contains("imageStore("), "raster should have no imageStore calls");
        assertFalse(r.contains("writeonly uniform uimage"), "raster should have no writeonly image decls");
        assertFalse(norm(r).contains("uniform uimage3D voxel_img"), "raster should have no uimage3D voxel_img decl");
        assertTrue(r.contains("main"), "raster keeps main");
    }

    @Test void directives_preservedThroughRasterStrip() {
        final var result = RwImageStoreExtractor.tryExtract(MINIMAL_VOX_VSH, PatchShaderType.VERTEX, "shadow");
        final String r = result.strippedSource();
        assertTrue(r.contains("#version"), "raster keeps a version directive");
        assertTrue(r.contains("#extension GL_ARB_shader_image_load_store"), "raster preserves user #extension directive\n\n" + r);
    }

    @Test void knownAttributes_includesAllNineCanonicals() {
        for (String name : new String[]{"iris_Vertex","iris_Color","iris_MultiTexCoord0","iris_MultiTexCoord1", "mc_midTexCoord","at_tangent","iris_Normal","mc_Entity","at_midBlock"}) {
            assertNotNull(RwImageStoreExtractor.CHUNK_VERTEX_ATTRS.get(name), "missing " + name);
        }
    }

    @Test void knownAttributeVariants_includesCeleritasTypings() {
        final var variants = RwImageStoreExtractor.CHUNK_VERTEX_ATTRS;
        assertNotNull(variants.get("a_PosId").get("vec3"));
        assertNotNull(variants.get("a_PosId").get("uvec4"));
        assertNotNull(variants.get("a_Color").get("vec4"));
        assertNotNull(variants.get("a_TexCoord").get("vec2"));
        assertNotNull(variants.get("a_LightCoord").get("uint"));
        assertNotNull(variants.get("a_LightCoord").get("ivec2"));
        assertNotNull(variants.get("mc_Entity").get("vec4"));
        assertNotNull(variants.get("mc_Entity").get("uint"));
        assertNotNull(variants.get("mc_midTexCoord").get("vec4"));
        assertNotNull(variants.get("mc_midTexCoord").get("vec2"));
    }

    private static final String CELERITAS_PATCHED_VOX_VSH = String.join("\n",
        "#version 460 core",
        "#extension GL_ARB_shader_image_load_store : enable",
        "",
        "#ifdef USE_VERTEX_COMPRESSION",
        "in uvec4 a_PosId;",
        "in vec4 a_Color;",
        "in vec2 a_TexCoord;",
        "in ivec2 a_LightCoord;",
        "void _vert_init() {",
        "    uint dummy = a_PosId.w;",
        "}",
        "#else",
        "in vec3 a_PosId;",
        "in vec4 a_Color;",
        "in vec2 a_TexCoord;",
        "in uint a_LightCoord;",
        "void _vert_init() {",
        "    vec3 p = a_PosId;",
        "}",
        "#endif",
        "",
        "vec3 _vert_position;",
        "vec2 _vert_tex_diffuse_coord;",
        "ivec2 _vert_tex_light_coord;",
        "vec4 _vert_color;",
        "uint _draw_id;",
        "uint _material_params;",
        "",
        "in uint mc_Entity;",
        "in vec2 mc_midTexCoord;",
        "in vec3 iris_Normal;",
        "in vec4 at_midBlock;",
        "",
        "writeonly uniform uimage3D voxel_img;",
        "",
        "uniform vec3 u_RegionOffset;",
        "out vec4 outColor;",
        "",
        "void main() {",
        "    _vert_init();",
        "    outColor = a_Color * vec4(iris_Normal, 1.0);",
        "    gl_Position = vec4(a_PosId + u_RegionOffset, 1.0);",
        "    if (mc_Entity > 0u) {",
        "        imageStore(voxel_img, ivec3(at_midBlock.xyz * 127.0), uvec4(mc_Entity, 0u, 0u, 0u));",
        "    }",
        "}",
        "");

    @Test void celeritas_tryExtractProducesComputeSource() {
        final var result = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN");
        assertNotNull(result);
        assertNotNull(result.computeSource(), "compute source must be built for celeritas-patched VSH");
        assertTrue(result.writtenImages().contains("voxel_img"));
        assertEquals(RwImageStoreExtractor.RwExtractMode.CHUNK, result.mode());
    }

    @Test void celeritas_computeContainsLiveBranchGlobals() {
        final String s = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN").computeSource();
        assertTrue(s.contains("vec3 a_PosId;"), "uncompressed (live) a_PosId global");
        assertTrue(s.contains("vec4 a_Color;"));
        assertTrue(s.contains("vec2 a_TexCoord;"));
        assertTrue(s.contains("uint a_LightCoord;"));
        assertTrue(s.contains("uint mc_Entity;"));
        assertTrue(s.contains("vec2 mc_midTexCoord;"));
        assertTrue(s.contains("vec3 iris_Normal;"));
        assertTrue(s.contains("vec4 at_midBlock;"));
    }

    @Test void celeritas_computeContainsDeadBranchStubGlobals() {
        final String s = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN").computeSource();
        assertTrue(s.contains("uvec4 a_PosId;"), "compressed-branch stub a_PosId");
        assertTrue(s.contains("ivec2 a_LightCoord;"), "compressed-branch stub a_LightCoord");
        assertTrue(s.contains("#ifdef USE_VERTEX_COMPRESSION"), "expected #ifdef guard around dual-branch globals");
        assertTrue(s.contains("#else"));
        assertTrue(s.contains("#endif"));
    }

    @Test void celeritas_unpackContainsCeleritasFormulas() {
        final String s = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN").computeSource();
        assertTrue(s.contains("a_PosId = vec3(uintBitsToFloat(_vg_vbuf.data[base + 0u])"));
        assertTrue(s.contains("a_TexCoord = vec2(uintBitsToFloat(_vg_vbuf.data[base + 4u])"));
        assertTrue(s.contains("a_LightCoord = _vg_vbuf.data[base + 6u]"));
        assertTrue(s.contains("mc_Entity = _vg_vbuf.data[base + 10u]"), "celeritas-typed mc_Entity must be delivered as the raw packed uint");
        assertTrue(s.contains("mc_midTexCoord = vec2(float(_vg_vbuf.data[base + 7u] & 0xFFFFu),"), "mc_midTexCoord is an unnormalized ushort delivered raw by the vertex stage, so the compute prelude must not rescale it");
        assertTrue(s.contains("a_PosId = uvec4(0u, 0u, 0u, 0u)"));
        assertTrue(s.contains("a_LightCoord = ivec2(0, 0)"));
    }

    @Test void celeritas_computeDecodesVertexGlobals() {
        final String s = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN").computeSource();
        assertTrue(s.contains("_vert_position = a_PosId;"), "uncompressed branch must decode position\n\n" + s);
        assertTrue(s.contains("_vert_position = vec3(a_PosId.xyz) * VERT_POS_SCALE + VERT_POS_OFFSET;"), "compressed branch must decode position\n\n" + s);
        assertTrue(s.contains("_draw_id = (_vg_draw_params >> 8) & 0xFFu;"), "_get_draw_translation depends on _draw_id\n\n" + s);
    }

    @Test void celeritas_vertexDecodeComesAfterTheGlobalDeclarations() {
        final String s = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN").computeSource();
        for (String global : new String[]{"_vert_position", "_vert_tex_diffuse_coord", "_vert_color", "_vert_tex_light_coord", "_draw_id", "_material_params"}) {
            final int declared = s.indexOf(global + " ;");
            final int assigned = s.indexOf(global + " =");
            assertTrue(declared >= 0, global + " should be declared by the celeritas header\n\n" + s);
            assertTrue(assigned < 0 || declared < assigned, global + " is assigned at " + assigned + " before its declaration at " + declared + "\n\n" + s);
        }
    }

    @Test void celeritas_strippedHasNoImageStoreOrUimage() {
        final var result = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN");
        final String r = result.strippedSource();
        assertFalse(r.contains("imageStore("));
        assertFalse(norm(r).contains("uniform uimage3D voxel_img"));
    }

    private static final String MULTI_IMAGE_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "attribute vec4 mc_Entity;",
        "attribute vec4 at_midBlock;",
        "writeonly uniform uimage2D wsr_img;",
        "writeonly uniform uimage3D voxel_img;",
        "writeonly uniform uimage3D wsr_lod_img;",
        "void main() {",
        "    imageStore(voxel_img, ivec3(0), uvec4(0u));",
        "    imageStore(wsr_img, ivec2(0), uvec4(0u));",
        "    imageStore(wsr_lod_img, ivec3(0), uvec4(0u));",
        "}",
        "");

    @Test void compute_emitsExplicitBindingsForWriteonlyImages() {
        final String s = RwImageStoreExtractor.tryExtract(MULTI_IMAGE_VSH, PatchShaderType.VERTEX, "ATTRIBUTES").computeSource();
        assertTrue(s.contains("layout(binding = 0) writeonly uniform uimage3D voxel_img;"), "voxel_img must be binding 0 (alphabetical-first), missing in:\n" + s);
        assertTrue(s.contains("layout(binding = 1) writeonly uniform uimage2D wsr_img;"));
        assertTrue(s.contains("layout(binding = 2) writeonly uniform uimage3D wsr_lod_img;"));
    }

    private static final String NON_WRITEONLY_PREPARE_VSH = String.join("\n",
        "#version 460 core",
        "layout(r32i) uniform iimage2D endcrystal_img;",
        "uniform mat4 gbufferModelView;",
        "void main() {",
        "    ivec4 prev = imageLoad(endcrystal_img, ivec2(0, 0));",
        "    imageStore(endcrystal_img, ivec2(0, 0), ivec4(prev.x + 1));",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void nonWriteonlyImage_stripsFromRaster() {
        final var result = RwImageStoreExtractor.tryExtract(NON_WRITEONLY_PREPARE_VSH, PatchShaderType.VERTEX, "prepare");
        assertNotNull(result);
        assertEquals(RwImageStoreExtractor.RwExtractMode.COMPOSITE_VSH, result.mode(), "no chunk attrs -> composite-vsh");
        final String r = result.strippedSource();
        assertFalse(r.contains("iimage2D"), "raster must have no iimage2D decl\n\n" + r);
        assertFalse(r.contains("imageStore("), "raster must have no imageStore call");
        assertTrue(r.contains("ivec4(0)"), "imageLoad must be replaced by ivec4(0)\n\n" + r);
    }

    private static final String COMPOSITE_VSH = String.join("\n",
        "#version 460 core",
        "writeonly uniform uimage2D state_img;",
        "uniform mat4 gl_ModelViewProjectionMatrix;",
        "void main() {",
        "    gl_Position = ftransform();",
        "    imageStore(state_img, ivec2(gl_VertexID, 0), uvec4(1u));",
        "}",
        "");

    @Test void compositeVsh_modeAndPrelude() {
        final var result = RwImageStoreExtractor.tryExtract(COMPOSITE_VSH, PatchShaderType.VERTEX, "composite");
        assertNotNull(result);
        assertEquals(RwImageStoreExtractor.RwExtractMode.COMPOSITE_VSH, result.mode());
        final String s = result.computeSource();
        assertTrue(s.startsWith("// _vg_mode: composite-vsh\n"), "sentinel comment on line 1\n\n" + s);
        assertTrue(s.contains("const vec4 _vg_quad[4]"), "quad table missing\n\n" + s);
        assertTrue(s.contains("#define gl_Vertex _vg_gl_vertex"));
        assertTrue(s.contains("#define gl_VertexID _vg_gl_vertex_id"));
        assertTrue(s.contains("vec4 iris_ftransform()"));
        assertTrue(s.contains("if (vid >= 4u) return;"));
    }

    @Test void compositeVsh_rasterStillCompilesShape() {
        final var result = RwImageStoreExtractor.tryExtract(COMPOSITE_VSH, PatchShaderType.VERTEX, "composite");
        final String r = result.strippedSource();
        assertFalse(r.contains("imageStore("), "raster has no imageStore");
        assertFalse(r.contains("uimage2D"), "raster has no uimage2D decl");
    }

    private static final String COMPOSITE_FSH = String.join("\n",
        "#version 460 core",
        "writeonly uniform uimage2D out_img;",
        "in vec2 texcoord;",
        "out vec4 outColor;",
        "void main() {",
        "    ivec2 px = ivec2(gl_FragCoord.xy);",
        "    imageStore(out_img, px, uvec4(uint(px.x), uint(px.y), 0u, 0u));",
        "    outColor = vec4(0.0, 0.0, 0.0, 1.0);",
        "}",
        "");

    @Test void compositeFsh_modeAndPrelude() {
        final var result = RwImageStoreExtractor.tryExtract(COMPOSITE_FSH, PatchShaderType.FRAGMENT, "composite_fsh");
        assertNotNull(result);
        assertEquals(RwImageStoreExtractor.RwExtractMode.COMPOSITE_FSH, result.mode());
        final String s = result.computeSource();
        assertTrue(s.startsWith("// _vg_mode: composite-fsh\n"));
        assertTrue(s.contains("layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;"));
        assertTrue(s.contains("uniform ivec2 _vg_target_size;"));
        assertTrue(s.contains("#define gl_FragCoord _vg_gl_fragcoord_raw"));
        assertTrue(s.contains("vec2(px) + 0.5"));
    }

    @Test void compositeFsh_rasterKeepsOutColorButStripsImage() {
        final var result = RwImageStoreExtractor.tryExtract(COMPOSITE_FSH, PatchShaderType.FRAGMENT, "composite_fsh");
        final String r = result.strippedSource();
        assertFalse(r.contains("imageStore("));
        assertFalse(r.contains("uimage2D"));
        assertTrue(norm(r).contains("out vec4 outColor"), "raster keeps user output\n\n" + r);
    }

    private static final String ATOMIC_IMAGE_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "attribute vec4 mc_Entity;",
        "attribute vec4 at_midBlock;",
        "layout(r32ui) uniform uimage3D counter_img;",
        "void main() {",
        "    imageAtomicAdd(counter_img, ivec3(0, 0, 0), 1u);",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void imageAtomic_isClassifiedAsWrite() {
        final var result = RwImageStoreExtractor.tryExtract(ATOMIC_IMAGE_VSH, PatchShaderType.VERTEX, "atomic");
        assertNotNull(result);
        assertTrue(result.writtenImages().contains("counter_img"), "imageAtomicAdd must classify the image as written");
        assertFalse(result.strippedSource().contains("imageAtomicAdd("), "imageAtomic call must be stripped from raster");
    }

    private static final String MIXED_IMAGES_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "attribute vec4 mc_Entity;",
        "attribute vec4 at_midBlock;",
        "uniform image2D voxel_a;",
        "uniform iimage2D voxel_b;",
        "uniform uimage3D voxel_c;",
        "void main() {",
        "    vec4 a = imageLoad(voxel_a, ivec2(0));",
        "    ivec4 b = imageLoad(voxel_b, ivec2(0));",
        "    uvec4 c = imageLoad(voxel_c, ivec3(0));",
        "    imageStore(voxel_a, ivec2(0), a);",
        "    imageStore(voxel_b, ivec2(0), b);",
        "    imageStore(voxel_c, ivec3(0), c);",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void mixedReadWrite_componentTypesCorrect() {
        final var result = RwImageStoreExtractor.tryExtract(MIXED_IMAGES_VSH, PatchShaderType.VERTEX, "mixed");
        assertNotNull(result);
        final String r = result.strippedSource();
        assertTrue(r.contains("vec4(0.0)"), "image2D imageLoad replaced by vec4(0.0)\n\n" + r);
        assertTrue(r.contains("ivec4(0)"), "iimage2D imageLoad replaced by ivec4(0)");
        assertTrue(r.contains("uvec4(0u)"), "uimage3D imageLoad replaced by uvec4(0u)");
    }

    private static final String ATOMIC_NOT_WRITEONLY_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "in uint mc_Entity;",
        "in vec2 mc_midTexCoord;",
        "in vec4 at_midBlock;",
        "layout(r32i) uniform iimage2D endcrystal_img;",
        "void main() {",
        "    imageAtomicAdd(endcrystal_img, ivec2(0, 0), 1);",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void atomicOnlyImage_doesNotGetWriteonlyQualifier() {
        final var result = RwImageStoreExtractor.tryExtract(ATOMIC_NOT_WRITEONLY_VSH, PatchShaderType.VERTEX, "atomic_only");
        assertNotNull(result);
        final String s = result.computeSource();
        assertFalse(s.contains("writeonly uniform iimage2D endcrystal_img"), "imageAtomic-only image must not get writeonly qualifier (atomics are read+write)\n\n" + s);
        assertTrue(s.contains("uniform iimage2D endcrystal_img"), "image decl still emitted in prelude\n\n" + s);
    }

    @Test void writeonlyImage_stillGetsWriteonlyQualifier() {
        final var result = RwImageStoreExtractor.tryExtract(MULTI_IMAGE_VSH, PatchShaderType.VERTEX, "writeonly");
        final String s = result.computeSource();
        assertTrue(s.contains("writeonly uniform uimage3D voxel_img"),
            "imageStore-only image keeps writeonly qualifier\n\n" + s);
    }

    private static final String IRIS_INJECTED_MC_ENTITY_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "in uint mc_Entity;",
        "in vec2 mc_midTexCoord;",
        "in vec4 at_midBlock;",
        "writeonly uniform uimage3D voxel_img;",
        "vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);",
        "void main() {",
        "    imageStore(voxel_img, ivec3(at_midBlock.xyz * 127.0), uvec4(iris_Entity.x, 0u, 0u, 0u));",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void astDeclaredMcEntity_emitsOnlyUintVariant() {
        final var result = RwImageStoreExtractor.tryExtract(IRIS_INJECTED_MC_ENTITY_VSH, PatchShaderType.VERTEX, "shadow");
        final String s = result.computeSource();
        assertFalse(s.contains("vec4 mc_Entity"), "mc_Entity declared in AST as uint must not emit a vec4 variant (Iris's iris_Entity uses mc_Entity >> 1u)\n\n" + s);
        assertTrue(s.contains("uint mc_Entity;"), "mc_Entity must be emitted as uint matching the AST decl\n\n" + s);
        assertFalse(s.contains("vec4 mc_midTexCoord"), "mc_midTexCoord declared in AST as vec2 must not emit a vec4 variant\n\n" + s);
        assertTrue(s.contains("vec2 mc_midTexCoord;"));
    }

    @Test void celeritasIfdefAttrs_emitBothVariantsGuarded() {
        final var result = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN");
        final String s = result.computeSource();
        assertTrue(s.contains("uvec4 a_PosId;"), "compressed-branch stub a_PosId\n\n" + s);
        assertTrue(s.contains("vec3 a_PosId;"), "uncompressed-branch live a_PosId\n\n" + s);
    }

    private static final String IMAGE_STORE_AS_BRACELESS_IF_BODY_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "in uint mc_Entity;",
        "in vec2 mc_midTexCoord;",
        "in vec4 at_midBlock;",
        "writeonly uniform uimage3D voxel_img;",
        "void main() {",
        "    if (gl_VertexID % 4 == 0)",
        "        imageStore(voxel_img, ivec3(0), uvec4(0u));",
        "    gl_Position = vec4(iris_Vertex.xyz, 1.0);",
        "}",
        "");

    @Test void raster_brackelessIfBodyImageStore_stripDoesNotLeaveDanglingIf() {
        final var result = RwImageStoreExtractor.tryExtract(IMAGE_STORE_AS_BRACELESS_IF_BODY_VSH, PatchShaderType.VERTEX, "if_body");
        assertNotNull(result);
        final String r = result.strippedSource();
        assertFalse(r.contains("imageStore("), "imageStore must be removed\n\n" + r);
        final String nr = norm(r);
        assertTrue(nr.contains("if (gl_VertexID % 4 == 0) ;"), "if statement must have a valid empty body after strip\n\n" + r);
    }

    @Test void raster_preservesIfdefBlockAndItsContents() {
        final var result = RwImageStoreExtractor.tryExtract(CELERITAS_PATCHED_VOX_VSH, PatchShaderType.VERTEX, "CELERITAS_TERRAIN");
        final String r = result.strippedSource();
        assertTrue(r.contains("#ifdef USE_VERTEX_COMPRESSION"), "raster must preserve the #ifdef block verbatim so _vert_init() resolves\n\n" + r);
        assertTrue(r.contains("#else"), "raster preserves the #else branch\n\n" + r);
        assertTrue(r.contains("#endif"), "raster preserves the #endif\n\n" + r);
        assertTrue(r.contains("void _vert_init()"), "raster must keep the _vert_init definition the user's main calls\n\n" + r);
        assertFalse(r.contains("imageStore("), "raster still strips imageStore\n\n" + r);
        assertFalse(norm(r).contains("uniform uimage3D voxel_img"), "raster still strips the image decl\n\n" + r);
    }

    @Test void atomicCall_rewrittenToInlineCompoundStatement() {
        final var result = RwImageStoreExtractor.tryExtract(ATOMIC_NOT_WRITEONLY_VSH, PatchShaderType.VERTEX, "atomic");
        assertNotNull(result);
        final String c = result.computeSource();
        final String nc = norm(c);
        assertFalse(nc.contains("imageAtomicAdd ("), "imageAtomicAdd must be rewritten in compute output\n\n" + c);
        assertTrue(nc.contains("int _vg_prev = imageLoad ( endcrystal_img"), "inline emulation must load the prior value with the original global name\n\n" + c);
        assertTrue(nc.contains("imageStore ( endcrystal_img"), "inline emulation must write the new value back to the original global\n\n" + c);
        assertTrue(nc.contains("ivec4 ( _vg_prev +"), "inline emulation must compute prev + val in the destination vec\n\n" + c);
    }

    @Test void atomicEmulation_droppedWriteonlyQualifier() {
        final var result = RwImageStoreExtractor.tryExtract(ATOMIC_NOT_WRITEONLY_VSH, PatchShaderType.VERTEX, "atomic");
        final String c = result.computeSource();
        assertFalse(c.contains("writeonly uniform iimage2D endcrystal_img"), "image used by imageAtomic* must not get writeonly qualifier (now load+store)\n\n" + c);
        assertTrue(c.contains("uniform iimage2D endcrystal_img"), "image decl present in prelude\n\n" + c);
    }

    private static final String MIXED_ATOMIC_AND_STORE_VSH = String.join("\n",
        "#version 460 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec4 iris_MultiTexCoord0;",
        "in vec3 iris_Normal;",
        "in uint mc_Entity;",
        "in vec2 mc_midTexCoord;",
        "in vec4 at_midBlock;",
        "layout(r32i) uniform iimage2D dual_img;",
        "void main() {",
        "    imageStore(dual_img, ivec2(0), ivec4(42, 0, 0, 0));",
        "    imageAtomicAdd(dual_img, ivec2(1, 0), 1);",
        "    gl_Position = vec4(0.0);",
        "}",
        "");

    @Test void mixedAtomicAndStore_bothRewritten() {
        final var result = RwImageStoreExtractor.tryExtract(MIXED_ATOMIC_AND_STORE_VSH, PatchShaderType.VERTEX, "mixed_atom");
        assertNotNull(result);
        final String c = result.computeSource();
        final String nc = norm(c);
        assertFalse(nc.contains("imageAtomicAdd ("), "atomic call should be rewritten\n\n" + c);
        assertTrue(nc.contains("int _vg_prev = imageLoad ( dual_img"), "atomic call should be inlined as load+store on dual_img\n\n" + c);
        int storeCount = 0;
        int from = 0;
        while ((from = nc.indexOf("imageStore ( dual_img", from)) >= 0) { storeCount++; from++; }
        assertTrue(storeCount >= 2, "expected both the user's imageStore and the emulated imageStore on dual_img, got " + storeCount + ":\n\n" + c);
    }
}
