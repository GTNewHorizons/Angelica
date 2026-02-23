package com.gtnewhorizons.angelica.glsm;

import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

/**
 * Compat transformation for mod shaders running under core profile.
 *
 * <p>Handles:
 * <ul>
 *   <li>Matrix builtin replacement (gl_ModelViewMatrix, gl_ProjectionMatrix, etc.)</li>
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

    /** Compat builtins that trigger AST transformation.  */
    private static final Set<String> COMPAT_BUILTINS = Set.of(
        "gl_ModelView", "gl_Projection", "gl_NormalMatrix", "gl_TextureMatrix",
        "gl_FragColor", "gl_Fog", "gl_FrontColor", "gl_Color",
        "texture2D", "texture3D", "texelFetch2D", "texelFetch3D", "textureSize2D",
        "shadow2D"
    );

    private static final Pattern NEEDS_TRANSFORM_PATTERN = Pattern.compile(String.join("|", COMPAT_BUILTINS));

    private static final Map<String, String> MATRIX_RENAMES = Map.of(
        "gl_ModelViewMatrix", "angelica_ModelViewMatrix",
        "gl_ModelViewMatrixInverse", "angelica_ModelViewMatrixInverse",
        "gl_ProjectionMatrix", "angelica_ProjectionMatrix",
        "gl_ProjectionMatrixInverse", "angelica_ProjectionMatrixInverse",
        "gl_NormalMatrix", "angelica_NormalMatrix"
    );

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
        if (!needsTransformation(source)) {
            return fixupVersion(source);
        }

        final CacheKey key = new CacheKey(source, isFragment);
        final String cached = cache.get(key);
        if (cached != null) return cached;

        try {
            final String result = transformInternal(source, isFragment);
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            LOGGER.warn("CompatShaderTransformer: AST transformation failed, falling back to version fixup only", e);
            return fixupVersion(source);
        }
    }

    private static boolean needsTransformation(String source) {
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

        // gl_FrontColor (vertex) → gl_Color (fragment) varying chain
        if (!isFragment) {
            transformer.injectVariable("out vec4 angelica_FrontColor;");
            transformer.rename("gl_FrontColor", "angelica_FrontColor");
            transformer.prependMain("angelica_FrontColor = vec4(1.0);");
        } else {
            transformer.injectVariable("in vec4 angelica_FrontColor;");
            transformer.rename("gl_Color", "angelica_FrontColor");
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
        final String header = versionDirective + (extensions.isEmpty() ? "" : "\n" + extensions);
        final StringBuilder result = new StringBuilder();
        transformer.mutateTree(tree -> result.append(GlslTransformUtils.getFormattedShader(tree, header)));

        // Restore pre-parse renames
        return GlslTransformUtils.restoreReservedWords(result.toString());
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
