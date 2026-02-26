package com.gtnewhorizons.angelica.glsm;

import net.minecraft.launchwrapper.Launch;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

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

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");

    private static final Pattern DEFINE_PATTERN =
        Pattern.compile("^\\s*#\\s*define\\s+.+$", Pattern.MULTILINE);

    /** Compat builtins that trigger AST transformation.  */
    private static final Set<String> COMPAT_BUILTINS = Set.of(
        "gl_ModelView", "gl_Projection", "gl_NormalMatrix", "gl_TextureMatrix",
        "gl_FragColor", "gl_Fog", "gl_FrontColor", "gl_Color",
        "gl_Vertex", "gl_MultiTexCoord", "gl_TexCoord", "gl_Normal", "ftransform",
        "texture2D", "texture3D", "texelFetch2D", "texelFetch3D", "textureSize2D",
        "shadow2D", "gl_FrontLightModelProduct"
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
        boolean isDev = false;
        try {
            final Object deobfEnv = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
            isDev = Boolean.TRUE.equals(deobfEnv);
        } catch (Exception ignored) {}
        DUMP_DIR = (isDev || Boolean.parseBoolean(System.getProperty("angelica.compat.dumpShaders", "false")))
            ? Paths.get("compat_shaders") : null;
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
                LOGGER.warn("CompatShaderTransformer: AST transformation failed, falling back to version fixup only", e);
                result = fixupVersion(source);
            }
        }

        dumpShader(source, result, isFragment, needsTransform);
        return result;
    }

    private static boolean needsTransformation(String source) {
        // Shaders already at 330+ core don't need compat transformation
        final Matcher vm = VERSION_PATTERN.matcher(source);
        if (vm.find()) {
            final int version = Integer.parseInt(vm.group(1));
            if (version >= 330 && "core".equals(vm.group(2))) return false;
        }

        return NEEDS_TRANSFORM_PATTERN.matcher(source).find();
    }

    private static String transformInternal(String source, boolean isFragment) {
        final Matcher versionMatcher = VERSION_PATTERN.matcher(source);
        int declaredVersion = 110;
        if (versionMatcher.find()) {
            declaredVersion = Integer.parseInt(versionMatcher.group(1));
        }

        final int targetVersion = Math.max(declaredVersion, 330);

        // Pre-parse reserved word renaming — prevents ANTLR parse failures
        source = GlslTransformUtils.replaceTexture(source);
        source = GlslTransformUtils.renameReservedWords(source, targetVersion);

        final ShaderParser.ParsedShader parsedShader = ShaderParser.parseShader(source);
        final Transformer transformer = new Transformer(parsedShader.full());

        injectMatrixUniforms(transformer);

        transformFog(transformer, isFragment);

        // gl_FrontLightModelProduct.sceneColor → angelica_SceneColor uniform
        if (source.contains("gl_FrontLightModelProduct")) {
            transformer.injectVariable("uniform vec4 angelica_SceneColor;");
            transformer.replaceExpression("gl_FrontLightModelProduct.sceneColor", "angelica_SceneColor");
        }

        // gl_FrontColor (vertex) → gl_Color (fragment) varying chain
        if (!isFragment) {
            transformer.injectVariable("out vec4 angelica_FrontColor;");
            transformer.rename("gl_FrontColor", "angelica_FrontColor");
            transformer.prependMain("angelica_FrontColor = vec4(1.0);");

            // Vertex attributes — replaces removed FFP vertex inputs with explicit in declarations
            transformVertexAttributes(transformer, source);
        } else {
            transformer.injectVariable("in vec4 angelica_FrontColor;");
            transformer.rename("gl_Color", "angelica_FrontColor");
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
        transformer.replaceExpression("gl_TextureMatrix", "mat4[8](mat4(1.0), angelica_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))");
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

    private static void transformFog(Transformer transformer, boolean isFragment) {
        transformer.rename("gl_FogFragCoord", "angelica_FogFragCoord");
        if (!isFragment) {
            transformer.injectVariable("out float angelica_FogFragCoord;");
            transformer.prependMain("angelica_FogFragCoord = 0.0f;");
        } else {
            transformer.injectVariable("in float angelica_FogFragCoord;");
        }

        transformer.rename("gl_Fog", "angelica_Fog");
        transformer.injectVariable("uniform float angelica_FogDensity;");
        transformer.injectVariable("uniform float angelica_FogStart;");
        transformer.injectVariable("uniform float angelica_FogEnd;");
        transformer.injectVariable("uniform vec4 angelica_FogColor;");
        transformer.injectFunction("struct angelica_FogParameters {vec4 color;float density;float start;float end;float scale;};");
        transformer.injectFunction(
            "angelica_FogParameters angelica_Fog = angelica_FogParameters("
            + "angelica_FogColor, angelica_FogDensity, angelica_FogStart, angelica_FogEnd, "
            + "1.0f / (angelica_FogEnd - angelica_FogStart));");
    }

    /**
     * Replace removed FFP vertex attributes with explicit {@code in} declarations at core profile attribute locations.
     */
    private static void transformVertexAttributes(Transformer transformer, String source) {
        if (source.contains("gl_Vertex") || source.contains("ftransform")) {
            transformer.injectVariable("layout(location = 0) in vec4 angelica_Vertex;");
            transformer.rename("gl_Vertex", "angelica_Vertex");
        }
        // gl_Color in vertex shaders is the per-vertex color attribute, distinct from the fragment gl_Color (interpolated gl_FrontColor) handled above
        if (source.contains("gl_Color")) {
            transformer.injectVariable("layout(location = 1) in vec4 angelica_Color;");
            transformer.rename("gl_Color", "angelica_Color");
        }
        if (source.contains("gl_MultiTexCoord0")) {
            transformer.injectVariable("layout(location = 2) in vec4 angelica_MultiTexCoord0;");
            transformer.rename("gl_MultiTexCoord0", "angelica_MultiTexCoord0");
        }
        if (source.contains("gl_MultiTexCoord1")) {
            transformer.injectVariable("layout(location = 3) in vec4 angelica_MultiTexCoord1;");
            transformer.rename("gl_MultiTexCoord1", "angelica_MultiTexCoord1");
        }
        if (source.contains("gl_Normal")) {
            transformer.injectVariable("layout(location = 4) in vec3 angelica_Normal;");
            transformer.rename("gl_Normal", "angelica_Normal");
        }
        if (source.contains("ftransform")) {
            transformer.replaceExpression("ftransform()", "(angelica_ProjectionMatrix * angelica_ModelViewMatrix * angelica_Vertex)");
        }
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
            LOGGER.warn("Failed to dump compat shader: {}", e.getMessage());
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
