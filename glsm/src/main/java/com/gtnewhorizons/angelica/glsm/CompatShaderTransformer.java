package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Compat transformation for mod shaders running under core profile.
 *
 * <p>Handles:
 * <ul>
 *   <li>Matrix builtin replacement (gl_ModelViewMatrix, gl_ProjectionMatrix, etc.)</li>
 *   <li>Vertex attribute replacement (gl_Vertex, gl_Color, gl_MultiTexCoord0/1, gl_Normal)</li>
 *   <li>gl_TexCoord[N] varying array → per-index in/out declarations</li>
 *   <li>Texture function renames (texture2D → texture, etc.)</li>
 *   <li>Fragment output handling (gl_FragColor → layout-qualified out declarations)</li>
 *   <li>Fog builtins (gl_Fog, gl_FogFragCoord)</li>
 *   <li>gl_FrontColor → local variable in vertex shaders</li>
 *   <li>shadow2D/shadow2DLod → texture/textureLod with vec4 wrapping</li>
 *   <li>Version upgrade to 330 core minimum</li>
 *   <li>Reserved word pre-parse renaming (texture-as-variable, sample, etc.)</li>
 * </ul>
 */
public class CompatShaderTransformer {

    private static final Pattern VERSION_PATTERN = Pattern.compile("#version[ \\t]+(\\d+)(?:[ \\t]+(\\w+))?");

    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*#\\s*define\\s+.+$", Pattern.MULTILINE);

    /** Compat builtins that trigger AST transformation. */
    private static final Set<String> COMPAT_BUILTINS = Set.of(
        "gl_ModelView", "gl_Projection", "gl_NormalMatrix", "gl_TextureMatrix",
        "gl_FragColor", "gl_Fog", "gl_FrontColor", "gl_Color",
        "gl_Vertex", "gl_MultiTexCoord", "gl_TexCoord", "gl_Normal", "ftransform",
        "texture2D", "texture3D", "texelFetch2D", "texelFetch3D", "textureSize2D",
        "shadow2D", "gl_FrontLightModelProduct",
        "gl_LightSource", "gl_FrontMaterial"
    );

    private static final Pattern NEEDS_TRANSFORM_PATTERN = Pattern.compile(
        String.join("|", COMPAT_BUILTINS) + "|\\b(?:attribute|varying)\\b"
    );

    private static final Map<String, String> MATRIX_RENAMES = Map.of(
        "gl_ModelViewMatrix", "angelica_ModelViewMatrix",
        "gl_ModelViewMatrixInverse", "angelica_ModelViewMatrixInverse",
        "gl_ProjectionMatrix", "angelica_ProjectionMatrix",
        "gl_ProjectionMatrixInverse", "angelica_ProjectionMatrixInverse",
        "gl_NormalMatrix", "angelica_NormalMatrix"
    );

    private static final Path DUMP_DIR;
    private static final AtomicInteger dumpCounter = new AtomicInteger(0);

    static {
        DUMP_DIR = Boolean.parseBoolean(System.getProperty("angelica.dumpShaders", "false")) ? Paths.get("compat_shaders") : null;
    }

    private static final int CACHE_SIZE = 32;
    private record CacheKey(String source, boolean isFragment) {}
    private static final Map<CacheKey, String> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(32, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<CacheKey, String> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public static void clearCache() {
        cache.clear();
    }

    /**
     * Transform a mod shader source for core profile compatibility.
     */
    public static String transform(String source, boolean isFragment) {
        final boolean needsTransform = needsTransformation(source);
        String result;
        if (!needsTransform) {
            result = fixupVersion(source);
        } else {
            final CacheKey key = new CacheKey(source, isFragment);
            final String cached = cache.get(key);
            if (cached != null) {
                dumpShader(source, cached, isFragment, needsTransform);
                return cached;
            }

            try {
                result = transformInternal(source, isFragment);
                cache.put(key, result);
            } catch (Exception e) {
                GLStateManager.LOGGER.warn("CompatShaderTransformer: AST transformation failed, falling back to version fixup only", e);
                result = fixupVersion(source);
            }
        }

        dumpShader(source, result, isFragment, needsTransform);
        return result;
    }

    public static boolean isCoreShader(String source) {
        final Matcher vm = VERSION_PATTERN.matcher(source);
        if (vm.find()) {
            final int version = Integer.parseInt(vm.group(1));
            return version >= 330 && "core".equals(vm.group(2));
        }
        return false;
    }

    private static boolean needsTransformation(String source) {
        if (isCoreShader(source)) return false;

        return NEEDS_TRANSFORM_PATTERN.matcher(source).find();
    }

    private static String transformInternal(String source, boolean isFragment) {
        final Matcher versionMatcher = VERSION_PATTERN.matcher(source);
        int declaredVersion = 110;
        if (versionMatcher.find()) {
            declaredVersion = Integer.parseInt(versionMatcher.group(1));
        }

        final int targetVersion = Math.max(declaredVersion, RENDER_BACKEND != null ? RENDER_BACKEND.getMinGLSLVersion() : 330);

        // Pre-parse reserved word renaming — prevents ANTLR parse failures
        source = GlslTransformUtils.replaceTexture(source);
        source = GlslTransformUtils.renameReservedWords(source, targetVersion);

        final ShaderParser.ParsedShader parsedShader = ShaderParser.parseShader(source);
        final Transformer transformer = new Transformer(parsedShader.full());

        injectMatrixUniforms(transformer);

        transformFog(transformer, isFragment, source);

        // gl_FrontLightModelProduct.sceneColor → angelica_SceneColor uniform
        if (source.contains("gl_FrontLightModelProduct")) {
            transformer.injectVariable("uniform vec4 angelica_SceneColor;");
            transformer.replaceExpression("gl_FrontLightModelProduct.sceneColor", "angelica_SceneColor");
        }

        // gl_LightSource[i] → struct + uniform array (both via injectFunction to keep struct before uniform)
        if (source.contains("gl_LightSource")) {
            transformer.injectFunction("struct angelica_LightSourceParameters {"
                    + "vec4 ambient;vec4 diffuse;vec4 specular;vec4 position;vec4 halfVector;"
                    + "vec3 spotDirection;float spotExponent;float spotCutoff;float spotCosCutoff;"
                    + "float constantAttenuation;float linearAttenuation;float quadraticAttenuation;"
                    + "};");
            transformer.injectFunction("uniform angelica_LightSourceParameters angelica_LightSource[2];");
            transformer.rename("gl_LightSource", "angelica_LightSource");
        }

        // gl_FrontMaterial → struct + uniform (both via injectFunction to keep struct before uniform)
        if (source.contains("gl_FrontMaterial")) {
            transformer.injectFunction("struct angelica_MaterialParameters {"
                    + "vec4 emission;vec4 ambient;vec4 diffuse;vec4 specular;float shininess;"
                    + "};");
            transformer.injectFunction("uniform angelica_MaterialParameters angelica_FrontMaterial;");
            transformer.rename("gl_FrontMaterial", "angelica_FrontMaterial");
        }

        // gl_FrontColor (vertex) → gl_Color (fragment) varying chain
        // Vertex side is unconditional: fragment may read gl_Color without vertex writing gl_FrontColor.
        // Uses angelica_ prefix to avoid colliding with user-declared varyings (e.g. "v_Color").
        if (!isFragment) {
            transformer.injectVariable("out vec4 angelica_FrontColor;");
            transformer.rename("gl_FrontColor", "angelica_FrontColor");
            transformer.prependMain("angelica_FrontColor = vec4(1.0);");

            final FFPInputs ffp = FFPInputs.detect(source);
            assignVertexInputLocations(transformer, ffp);
            transformVertexAttributes(transformer, source, ffp);
        } else {
            if (source.contains("gl_Color")) {
                transformer.injectVariable("in vec4 angelica_FrontColor;");
                transformer.rename("gl_Color", "angelica_FrontColor");
            }
        }

        // gl_TexCoord[N] varying array → per-index in/out declarations
        final Set<Integer> texCoordIndices = new HashSet<>();
        transformer.renameArray("gl_TexCoord", "angelica_TexCoord", texCoordIndices);
        for (Integer i : texCoordIndices) {
            final String qualifier = isFragment ? "in" : "out";
            transformer.injectVariable(qualifier + " vec4 angelica_TexCoord" + i + ";");
        }

        // Fragment output handling + alpha test discard
        if (isFragment) {
            transformFragmentOutputs(transformer);
        }

        // texture-as-variable collision handling
        if (transformer.containsCall("texture") && transformer.hasVariable("texture")) {
            transformer.rename("texture", "gtexture");
        }
        if (transformer.hasVariable("angelica_renamed_texture")) {
            transformer.rename("angelica_renamed_texture", "gtexture");
        }

        transformer.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES);

        transformer.renameAndWrapShadow("shadow2D", "texture");
        transformer.renameAndWrapShadow("shadow2DLod", "textureLod");

        final String versionDirective = "#version " + targetVersion + " core\n";
        final String extensions = VERSION_PATTERN.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

        // Preserve #define directives
        final StringBuilder defines = new StringBuilder();
        final Matcher defineMatcher = DEFINE_PATTERN.matcher(source);
        while (defineMatcher.find()) {
            defines.append(defineMatcher.group().trim()).append('\n');
        }

        final String header = versionDirective + (extensions.isEmpty() ? "" : "\n" + extensions) + (defines.isEmpty() ? "" : "\n" + defines);
        final StringBuilder result = new StringBuilder();
        transformer.mutateTree(tree -> result.append(GlslTransformUtils.getFormattedShader(tree, header)));

        // Restore pre-parse renames
        String output = GlslTransformUtils.restoreReservedWords(result.toString());

        // Core profile: attribute → in, varying → out (vertex) / in (fragment)
        output = fixupQualifiers(output, isFragment);

        return output;
    }

    /**
     * Inject matrix uniforms and rename compat builtins.
     */
    private static void injectMatrixUniforms(Transformer transformer) {
        transformer.injectVariable("uniform mat4 angelica_ModelViewMatrix;");
        transformer.injectVariable("uniform mat4 angelica_ModelViewMatrixInverse;");
        transformer.injectVariable("uniform mat4 angelica_ProjectionMatrix;");
        transformer.injectVariable("uniform mat4 angelica_ProjectionMatrixInverse;");
        transformer.injectVariable("uniform mat3 angelica_NormalMatrix;");
        transformer.injectVariable("uniform mat4 angelica_LightmapTextureMatrix;");

        transformer.rename(MATRIX_RENAMES);

        // Expression replacements
        transformer.replaceExpression("gl_ModelViewProjectionMatrix", "(angelica_ProjectionMatrix * angelica_ModelViewMatrix)");
        transformer.replaceExpression("gl_TextureMatrix[0]", "mat4(1.0)");
        transformer.replaceExpression("gl_TextureMatrix[1]", "angelica_LightmapTextureMatrix");
        transformer.replaceExpression(
                "gl_TextureMatrix",
                "mat4[8](mat4(1.0), angelica_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))");
    }

    /**
     * Transform fragment outputs for core profile.
     */
    private static void transformFragmentOutputs(Transformer transformer) {
        if (transformer.containsCall("gl_FragColor")) {
            transformer.replaceExpression("gl_FragColor", "gl_FragData[0]");
        }

        final Set<Integer> found = new HashSet<>();
        transformer.renameArray("gl_FragData", "angelica_FragData", found);

        for (Integer i : found) {
            transformer.injectVariable("layout (location = " + i + ") out vec4 angelica_FragData" + i + ";");
        }

        // Core profile: GL_ALPHA_TEST is removed - inject runtime discard using GLSM-tracked alpha reference (uploaded by CompatUniformManager).
        if (found.contains(0)) {
            transformer.injectVariable("uniform float angelica_currentAlphaTest;");
            transformer.appendMain("if (angelica_FragData0.a <= angelica_currentAlphaTest) discard;");
        }
    }

    private static void transformFog(Transformer transformer, boolean isFragment, String source) {
        // Vertex side is unconditional: fragment may read gl_FogFragCoord without vertex writing it
        transformer.rename("gl_FogFragCoord", "angelica_FogFragCoord");
        if (!isFragment) {
            transformer.injectVariable("out float angelica_FogFragCoord;");
            transformer.prependMain("angelica_FogFragCoord = 0.0;");
        } else {
            if (source.contains("gl_FogFragCoord")) {
                transformer.injectVariable("in float angelica_FogFragCoord;");
            }
        }

        transformer.rename("gl_Fog", "angelica_Fog");
        transformer.injectVariable("uniform float angelica_FogDensity;");
        transformer.injectVariable("uniform float angelica_FogStart;");
        transformer.injectVariable("uniform float angelica_FogEnd;");
        transformer.injectVariable("uniform vec4 angelica_FogColor;");
        transformer.injectFunction("struct angelica_FogParameters {vec4 color;float density;float start;float end;float scale;};");
        transformer.injectFunction("angelica_FogParameters angelica_Fog = angelica_FogParameters("
                + "angelica_FogColor, angelica_FogDensity, angelica_FogStart, angelica_FogEnd, "
                + "1.0 / (angelica_FogEnd - angelica_FogStart));");
    }

    /**
     * Replace removed FFP vertex attributes with explicit {@code in} declarations at core profile attribute locations.
     */
    private static void transformVertexAttributes(Transformer transformer, String source, FFPInputs ffp) {
        if (ffp.position()) {
            transformer.injectVariable("layout(location = 0) in vec4 angelica_Vertex;");
            transformer.rename("gl_Vertex", "angelica_Vertex");
        }
        // gl_Color in vertex shaders is the per-vertex color attribute, distinct from the fragment gl_Color (interpolated gl_FrontColor) handled above
        if (ffp.color()) {
            transformer.injectVariable("layout(location = 1) in vec4 angelica_Color;");
            transformer.rename("gl_Color", "angelica_Color");
        }
        if (ffp.uv0()) {
            transformer.injectVariable("layout(location = 2) in vec4 angelica_MultiTexCoord0;");
            transformer.rename("gl_MultiTexCoord0", "angelica_MultiTexCoord0");
        }
        if (ffp.uv1()) {
            transformer.injectVariable("layout(location = 3) in vec4 angelica_MultiTexCoord1;");
            transformer.rename("gl_MultiTexCoord1", "angelica_MultiTexCoord1");
        }
        if (ffp.normal()) {
            transformer.injectVariable("layout(location = 4) in vec3 angelica_Normal;");
            transformer.rename("gl_Normal", "angelica_Normal");
        }
        if (source.contains("ftransform")) {
            transformer.replaceExpression("ftransform()", "(angelica_ProjectionMatrix * angelica_ModelViewMatrix * angelica_Vertex)");
        }
    }

    private static final int MAX_VERTEX_ATTRIBS = 16;

    private record FFPInputs(boolean position, boolean color, boolean uv0, boolean uv1, boolean normal) {
        static FFPInputs detect(String source) {
            return new FFPInputs(
                source.contains("gl_Vertex") || source.contains("ftransform"),
                source.contains("gl_Color"),
                source.contains("gl_MultiTexCoord0"),
                source.contains("gl_MultiTexCoord1"),
                source.contains("gl_Normal"));
        }
    }

    /** Adaptation of Mesa's assign_attribute_or_color_locations */
    private static void assignVertexInputLocations(Transformer transformer, FFPInputs ffp) {
        int used = 0;
        if (ffp.position()) used |= 1 << Usage.POSITION.getAttributeLocation();
        if (ffp.color()) used |= 1 << Usage.COLOR.getAttributeLocation();
        if (ffp.uv0()) used |= 1 << Usage.PRIMARY_UV.getAttributeLocation();
        if (ffp.uv1()) used |= 1 << Usage.SECONDARY_UV.getAttributeLocation();
        if (ffp.normal()) used |= 1 << Usage.NORMAL.getAttributeLocation();

        record Input(String name, String baseType, String arraySuffix, int slots, int index) {}
        final List<Input> toAssign = new ArrayList<>();
        int index = 0;

        for (TerminalNode storage : transformer.collectStorage()) {
            final int tokenType = storage.getSymbol().getType();
            if (tokenType != GLSLLexer.ATTRIBUTE && tokenType != GLSLLexer.IN) continue;

            ParseTree node = storage.getParent();
            while (node != null && !(node instanceof GLSLParser.Single_declarationContext)) {
                node = node.getParent();
            }
            if (!(node instanceof GLSLParser.Single_declarationContext decl)) continue;
            if (decl.typeless_declaration() == null) continue;

            final GLSLParser.Type_specifierContext typeSpec = decl.fully_specified_type().type_specifier();
            final String baseType = GlslTransformUtils.getFormattedShader(typeSpec.type_specifier_nonarray(), "").trim();
            final int typeArraySlots = arraySlots(typeSpec.array_specifier());
            final String typeArray = arraySuffix(typeSpec.array_specifier());

            if (hasLayoutQualifier(decl)) {
                final int loc = explicitLocation(decl);
                final int slots = baseSlots(baseType) * typeArraySlots * arraySlots(decl.typeless_declaration().array_specifier());
                if (loc >= 0) used |= slotMask(slots) << loc;
                continue;
            }

            final List<GLSLParser.Typeless_declarationContext> declarators = new ArrayList<>();
            declarators.add(decl.typeless_declaration());
            if (decl.getParent() instanceof GLSLParser.Init_declarator_listContext list) {
                declarators.addAll(list.typeless_declaration());
            }
            for (GLSLParser.Typeless_declarationContext d : declarators) {
                if (d.IDENTIFIER() == null) continue;
                final int slots = baseSlots(baseType) * typeArraySlots * arraySlots(d.array_specifier());
                final String arraySuffix = typeArray + arraySuffix(d.array_specifier());
                toAssign.add(new Input(d.IDENTIFIER().getText(), baseType, arraySuffix, slots, index++));
            }
        }

        toAssign.sort((a, b) -> a.slots() != b.slots() ? b.slots() - a.slots() : a.index() - b.index());

        for (Input in : toAssign) {
            final int loc = findAvailableSlots(used, in.slots());
            if (loc < 0) continue;
            used |= slotMask(in.slots()) << loc;
            transformer.removeVariable(in.name());
            transformer.variable = null;
            transformer.injectVariable("layout(location = " + loc + ") in " + in.baseType() + " " + in.name() + in.arraySuffix() + ";");
        }
    }

    private static String arraySuffix(GLSLParser.Array_specifierContext arraySpec) {
        return arraySpec != null ? GlslTransformUtils.getFormattedShader(arraySpec, "").trim() : "";
    }

    private static int arraySlots(GLSLParser.Array_specifierContext arraySpec) {
        if (arraySpec == null) return 1;
        int product = 1;
        for (GLSLParser.DimensionContext dim : arraySpec.dimension()) {
            if (dim.constant_expression() == null) continue;
            try {
                product *= Integer.decode(GlslTransformUtils.getFormattedShader(dim.constant_expression(), "").trim());
            } catch (NumberFormatException ignored) {}
        }
        return product;
    }

    private static int baseSlots(String type) {
        final String dims = type.startsWith("dmat") ? type.substring(4) : type.startsWith("mat") ? type.substring(3) : null;
        if (dims != null && !dims.isEmpty() && Character.isDigit(dims.charAt(0))) {
            return dims.charAt(0) - '0';
        }
        return 1;
    }

    private static int slotMask(int slots) {
        return (slots >= 32) ? -1 : (1 << slots) - 1;
    }

    private static int findAvailableSlots(int used, int needed) {
        if (needed <= 0) return -1;
        final int mask = slotMask(needed);
        for (int i = 0; i <= MAX_VERTEX_ATTRIBS - needed; i++) {
            if (((mask << i) & ~used) == (mask << i)) return i;
        }
        return -1;
    }

    private static int explicitLocation(GLSLParser.Single_declarationContext decl) {
        final GLSLParser.Type_qualifierContext qualifier = decl.fully_specified_type().type_qualifier();
        if (qualifier == null) return -1;
        for (GLSLParser.Single_type_qualifierContext single : qualifier.single_type_qualifier()) {
            final GLSLParser.Layout_qualifierContext layout = single.layout_qualifier();
            if (layout == null) continue;
            for (GLSLParser.Layout_qualifier_idContext id : layout.layout_qualifier_id_list().layout_qualifier_id()) {
                if (id.IDENTIFIER() != null && "location".equals(id.IDENTIFIER().getText()) && id.constant_expression() != null) {
                    try {
                        return Integer.decode(GlslTransformUtils.getFormattedShader(id.constant_expression(), "").trim());
                    } catch (NumberFormatException ignored) {
                        return -1;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean hasLayoutQualifier(GLSLParser.Single_declarationContext decl) {
        final GLSLParser.Type_qualifierContext qualifier = decl.fully_specified_type().type_qualifier();
        if (qualifier == null) return false;
        for (GLSLParser.Single_type_qualifierContext single : qualifier.single_type_qualifier()) {
            if (single.layout_qualifier() != null) return true;
        }
        return false;
    }

    private static void dumpShader(String original, String transformed, boolean isFragment, boolean wasTransformed) {
        if (DUMP_DIR == null) return;
        final int id = dumpCounter.getAndIncrement();
        final String suffix = isFragment ? ".frag.glsl" : ".vert.glsl";
        try {
            Files.createDirectories(DUMP_DIR);
            // Capture caller info for identification
            final String caller = identifyCaller();
            final String header = "// Compat shader dump #" + id + " (" + (isFragment ? "fragment" : "vertex") + ")"
                + "\n// Transformed: " + wasTransformed
                + "\n// Caller: " + caller + "\n\n";
            Files.writeString(DUMP_DIR.resolve(id + "_original" + suffix), header + original, StandardCharsets.UTF_8);
            Files.writeString(DUMP_DIR.resolve(id + "_transformed" + suffix), header + transformed, StandardCharsets.UTF_8);
        } catch (IOException e) {
            GLStateManager.LOGGER.warn("Failed to dump compat shader: {}", e.getMessage());
        }
    }

    private static String identifyCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            final String cls = frame.getClassName();
            if (!cls.startsWith("com.gtnewhorizons.angelica.glsm.")
                && !cls.startsWith("java.")
                && !cls.equals("org.lwjgl.opengl.GL20")) {
                return cls + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return "unknown";
    }

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("\\battribute\\b");
    private static final Pattern VARYING_PATTERN = Pattern.compile("\\bvarying\\b");

    /** Replace legacy storage qualifiers removed in core profile. Safe on AST-serialized output (no comments). */
    public static String fixupQualifiers(String source, boolean isFragment) {
        source = ATTRIBUTE_PATTERN.matcher(source).replaceAll("in");
        source = VARYING_PATTERN.matcher(source).replaceAll(isFragment ? "in" : "out");
        return source;
    }

    // Patterns for parsing fragment shader in declarations to generate passthrough vertex shaders
    private static final Pattern FRAG_IN_COLOR = Pattern.compile("\\bin\\s+vec4\\s+angelica_FrontColor\\b");
    private static final Pattern FRAG_IN_TEXCOORD = Pattern.compile("\\bin\\s+vec4\\s+angelica_TexCoord(\\d+)\\b");
    private static final Pattern FRAG_IN_FOGCOORD = Pattern.compile("\\bin\\s+float\\s+angelica_FogFragCoord\\b");

    /**
     * Generate a passthrough vertex shader for a fragment-only program.
     *
     * @param fragmentSource the transformed fragment shader source
     * @return a complete vertex shader source string
     */
    public static String generatePassthroughVertexShader(String fragmentSource) {
        final StringBuilder sb = new StringBuilder(512);
        sb.append("#version 330 core\n\n");

        // Always have position
        sb.append("layout(location = 0) in vec4 a_Position;\n");

        // Matrix uniforms for position transform
        sb.append("uniform mat4 angelica_ModelViewMatrix;\n");
        sb.append("uniform mat4 angelica_ProjectionMatrix;\n");

        final StringBuilder varyings = new StringBuilder();
        final StringBuilder assignments = new StringBuilder();
        if (FRAG_IN_COLOR.matcher(fragmentSource).find()) {
            sb.append("layout(location = 1) in vec4 a_Color;\n");
            varyings.append("out vec4 angelica_FrontColor;\n");
            assignments.append("  angelica_FrontColor = a_Color;\n");
        }

        final Matcher texCoordMatcher = FRAG_IN_TEXCOORD.matcher(fragmentSource);
        final Set<Integer> texCoordIndices = new HashSet<>();
        while (texCoordMatcher.find()) {
            texCoordIndices.add(Integer.parseInt(texCoordMatcher.group(1)));
        }
        for (int i : texCoordIndices) {
            // PRIMARY_UV=2, SECONDARY_UV=3. Only indices 0 and 1 are supported; higher indices collide at location 3.
            final int location = i == 0 ? 2 : 3;
            sb.append("layout(location = ").append(location).append(") in vec4 a_TexCoord").append(i).append(";\n");
            varyings.append("out vec4 angelica_TexCoord").append(i).append(";\n");
            assignments.append("  angelica_TexCoord").append(i).append(" = a_TexCoord").append(i).append(";\n");
        }

        if (FRAG_IN_FOGCOORD.matcher(fragmentSource).find()) {
            varyings.append("out float angelica_FogFragCoord;\n");
            assignments.append("  angelica_FogFragCoord = abs((angelica_ModelViewMatrix * a_Position).z);\n");
        }

        sb.append(varyings);
        sb.append("\nvoid main() {\n");
        sb.append("  gl_Position = angelica_ProjectionMatrix * angelica_ModelViewMatrix * a_Position;\n");
        sb.append(assignments);
        sb.append("}\n");

        return sb.toString();
    }

    /** Ensure #version is at least 330 core, strip 'compatibility' profile. */
    private static String fixupVersion(String source) {
        final Matcher m = VERSION_PATTERN.matcher(source);
        if (!m.find()) {
            return "#version 330 core\n" + source;
        }

        final int version = Integer.parseInt(m.group(1));
        final String profile = m.group(2);

        if (version >= 330 && !"compatibility".equals(profile)) {
            if ("core".equals(profile)) return source;
            return m.replaceFirst("#version " + version + " core");
        }

        final int targetVersion = Math.max(version, 330);
        return m.replaceFirst("#version " + targetVersion + " core");
    }
}
