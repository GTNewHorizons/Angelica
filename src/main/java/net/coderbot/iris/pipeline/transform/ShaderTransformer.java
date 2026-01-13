package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderTransformer {
    private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");
    private static final Pattern inOutVaryingPattern = Pattern.compile("(?m)^\\s*(in|out)(\\s+)");
    private static final Pattern inPattern = Pattern.compile("(?m)^\\s*(in)(\\s+)");
    private static final Pattern outPattern = Pattern.compile("(?m)^\\s*(out)(\\s+)");

    private static final int CACHE_SIZE = 100;
    private static final Object2ObjectLinkedOpenHashMap<TransformKey<?>, Map<PatchShaderType, String>> shaderTransformationCache = new Object2ObjectLinkedOpenHashMap<>();
    private static final boolean useCache = true;

    public static void clearCache() {
        synchronized (shaderTransformationCache) {
            shaderTransformationCache.clear();
        }
    }

    /**
     * These are words which need to be renamed by iris if a shader uses them, regardless o the GLSL version.
     * The words will get caught and renamed to iris_renamed_$WORD
     */
    private static final List<String> fullReservedWords = new ArrayList<>();

    /**
     * This does the same thing as fullReservedWords, but based on a maximum GLSL version. As an example
     * if something was register to 400 here, it would get applied on any version below 400.
     */
    private static final Map<Integer, List<String>> versionedReservedWords = new HashMap<>();

    static {
        // texture seems to be reserved by some drivers but not others, however this is not actually reserved by the GLSL spec
        fullReservedWords.add("texture");

        // sample was added as a keyword in GLSL 400, many shaders use it
        versionedReservedWords.put(400, Arrays.asList("sample"));
    }

    private static final class TransformKey<P extends Parameters> {
        private final Patch patchType;
        private final EnumMap<PatchShaderType, String> inputs;
        private final P params;

        private TransformKey(Patch patchType, EnumMap<PatchShaderType, String> inputs, P params) {
            this.patchType = patchType;
            this.inputs = inputs;
            this.params = params;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TransformKey<?>) obj;
            return Objects.equals(this.patchType, that.patchType) &&
                Objects.equals(this.inputs, that.inputs) &&
                Objects.equals(this.params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patchType, inputs, params);
        }

        @Override
        public String toString() {
            return "TransformKey[" +
                "patchType=" + patchType + ", " +
                "inputs=" + inputs + ", " +
                "params=" + params + ']';
        }
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String fragment, P parameters) {
        if (vertex == null && geometry == null && fragment == null) {
            return null;
        } else {
            Map<PatchShaderType, String> result;

            var patchType = parameters.patch;

            EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
            inputs.put(PatchShaderType.VERTEX, vertex);
            inputs.put(PatchShaderType.GEOMETRY, geometry);
            inputs.put(PatchShaderType.FRAGMENT, fragment);

            var key = new TransformKey<>(patchType, inputs, parameters);

            synchronized (shaderTransformationCache) {
                result = shaderTransformationCache.getAndMoveToLast(key);
            }
            if(result == null || !useCache) {
                result = transformInternal(inputs, patchType, parameters);
                // Clear this, we don't want whatever random type was last transformed being considered for the key
                parameters.type = null;
                synchronized (shaderTransformationCache) {
                    // Double-check in case another thread added it while we were transforming
                    Map<PatchShaderType, String> existing = shaderTransformationCache.getAndMoveToLast(key);
                    if (existing != null) {
                        return existing;
                    }
                    if(shaderTransformationCache.size() >= CACHE_SIZE) {
                        shaderTransformationCache.removeFirst();
                    }
                    shaderTransformationCache.putAndMoveToLast(key, result);
                }
            }

            return result;
        }
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);
        List<Transformer> textureLodExtensionPatches = new ArrayList<>();
        List<Transformer> hacky120Patches = new ArrayList<>();

        Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.VALUES) {
            parameters.type = type;
            if (inputs.get(type) == null) {
                continue;
            }

            String input = inputs.get(type);

            Matcher matcher = versionPattern.matcher(input);
            if (!matcher.find()) {
                throw new IllegalArgumentException("No #version directive found in source code!");
            }

            String versionString = matcher.group(1);
            if (versionString == null) {
                continue;
            }

            String profile = "";
            int versionInt = Integer.parseInt(versionString);
            if (versionInt >= 150) {
                profile = matcher.group(2);
                if (profile == null) {
                    profile = "core";
                }
            }

            String profileString = "#version " + versionString + " " + profile + "\n";

            // The primary reason we rename words here using regex, is because if the words cause invalid
            // GLSL, regardless of the version being used, it will cause glsl-transformation-lib to fail
            // so we need to rename them prior to passing the shader input to glsl-transformation-lib.
            for (String reservedWord : fullReservedWords) {
                String newName = "iris_renamed_" + reservedWord;
                input = input.replaceAll("\\b" + reservedWord + "\\b", newName);
            }
            for (int version : versionedReservedWords.keySet()) {
                if (versionInt < version) {
                    for (String reservedWord : versionedReservedWords.get(version)) {
                        String newName = "iris_renamed_" + reservedWord;
                        input = input.replaceAll("\\b" + reservedWord + "\\b", newName);
                    }
                }
            }

            var parsedShader = ShaderParser.parseShader(input);
            var transformer = new Transformer(parsedShader.full());

            doTransform(transformer, patchType, parameters, profile, versionInt);

            // Check if we need to patch in texture LOD extension enabling
            if (versionInt <= 120 && (transformer.containsCall("texture2DLod") || transformer.containsCall("texture3DLod") || transformer.containsCall("texture2DGradARB"))) {
                textureLodExtensionPatches.add(transformer);
            }

            // Check if we need to patch in some hacky GLSL 120 compat
            if (versionInt <= 120) {
                hacky120Patches.add(transformer);
            }

            types.put(type, transformer);
            prepatched.put(type, profileString);
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            final Transformer transformer = entry.getValue();
            String header = prepatched.get(entry.getKey());

            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header
            if (patchType == Patch.CELERITAS_TERRAIN && entry.getKey() == PatchShaderType.VERTEX) {
                header += computeCeleritasHeader();
            }

            final String finalHeader = header;
            final StringBuilder formattedShaderBuilder = new StringBuilder();

            transformer.mutateTree(tree -> {
                formattedShaderBuilder.append(getFormattedShader(tree, finalHeader));
            });

            String formattedShader = formattedShaderBuilder.toString();

            // Restore identifiers that were temporarily renamed to dodge GLSL reserved keywords.
            formattedShader = formattedShader.replace("iris_renamed_texture", "texture");
            formattedShader = formattedShader.replace("iris_renamed_sample", "sample");

            // Please don't mind the entire rest of this loop basically, we're doing awful fragile regex on the transformed
            // shader output to do things that I can't figure out with AST because I'm bad at it

            if (textureLodExtensionPatches.contains(transformer)) {
                String[] parts = formattedShader.split("\n", 2);
                parts[1] = "#extension GL_ARB_shader_texture_lod : require\n" + parts[1];
                formattedShader = parts[0] + "\n" + parts[1];
            }

            if (hacky120Patches.contains(transformer)) {
                // Forcibly enable GL_EXT_gpu_shader4, it has a lot of compatibility backports with GLSL 130+
                // and seems more or less universally supported by hardware/drivers
                String[] parts = formattedShader.split("\n", 2);
                parts[1] = "#extension GL_EXT_gpu_shader4 : require\n" + parts[1];
                formattedShader = parts[0] + "\n" + parts[1];

                // GLSL 120 compatibility for in/out specifiers:
                // - Vertex shaders: "in" = vertex attribute input -> "attribute", "out" = to fragment -> "varying"
                // - Fragment shaders: "in" = from vertex -> "varying", "out" = color output -> handled elsewhere
                if (entry.getKey() == PatchShaderType.VERTEX) {
                    // In vertex shaders, "in" declarations are vertex attributes, not varyings
                    Matcher inMatcher = inPattern.matcher(formattedShader);
                    formattedShader = inMatcher.replaceAll("attribute$2");
                    Matcher outMatcher = outPattern.matcher(formattedShader);
                    formattedShader = outMatcher.replaceAll("varying$2");
                } else {
                    // In fragment (and geometry) shaders, both in/out become varying
                    Matcher inOutVaryingMatcher = inOutVaryingPattern.matcher(formattedShader);
                    formattedShader = inOutVaryingMatcher.replaceAll("varying$2");
                }
            }

            result.put(entry.getKey(), formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, String profile, int versionInt) {
        switch (patchType) {
            case CELERITAS_TERRAIN:
                CeleritasTransformer.transform(transformer, parameters);
                // Handle mc_midTexCoord for Celeritas
                patchMultiTexCoord3(transformer, parameters);
                replaceMidTexCoord(transformer, IrisExtendedChunkVertexType.MID_TEX_SCALE);
                applyIntelHd4000Workaround(transformer);
                break;
            case COMPOSITE:
                CompositeDepthTransformer.transform(transformer);
                break;
            case ATTRIBUTES:
                AttributeTransformer.transform(transformer, (AttributeParameters) parameters, profile, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        CompatibilityTransformer.transformEach(transformer, parameters);
    }

    public static void applyIntelHd4000Workaround(Transformer transformer) {
        transformer.renameFunctionCall("ftransform", "iris_ftransform");
    }


    public static void replaceGlMultiTexCoordBounded(Transformer transformer, int min, int max) {
        for (int i = min; i <= max; i++) {
            transformer.replaceExpression("gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
        }
    }

    public static void patchMultiTexCoord3(Transformer transformer, Parameters parameters) {
        if (parameters.type.glShaderType == ShaderType.VERTEX && transformer.hasVariable("gl_MultiTexCoord3") && !transformer.hasVariable("mc_midTexCoord")) {
            transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
            transformer.injectVariable("attribute vec4 mc_midTexCoord;");
        }
    }

    public static void replaceMidTexCoord(Transformer transformer, float textureScale) {
        int type = transformer.findType("mc_midTexCoord");
        if (type != 0) {
            transformer.removeVariable("mc_midTexCoord");
        }
        transformer.replaceExpression("mc_midTexCoord", "iris_MidTex");
        switch (type) {
            case 0:
                return;
            case GLSLLexer.BOOL:
                return;
            case GLSLLexer.FLOAT:
                transformer.injectFunction("float iris_MidTex = (mc_midTexCoord.x * " + textureScale + ").x;"); //TODO go back to variable if order is fixed
                break;
            case GLSLLexer.VEC2:
                transformer.injectFunction("vec2 iris_MidTex = (mc_midTexCoord.xy * " + textureScale + ").xy;");
                break;
            case GLSLLexer.VEC3:
                transformer.injectFunction("vec3 iris_MidTex = vec3(mc_midTexCoord.xy * " + textureScale + ", 0.0);");
                break;
            case GLSLLexer.VEC4:
                transformer.injectFunction("vec4 iris_MidTex = vec4(mc_midTexCoord.xy * " + textureScale + ", 0.0, 1.0);");
                break;
            default:

        }

        transformer.injectVariable("in vec2 mc_midTexCoord;"); //TODO why is this inserted oddly?

    }

    public static void addIfNotExists(Transformer transformer, String name, String code) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(code);
        }
    }

    public static void addIfNotExistsType(Transformer transformer, String name, String type) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(type + " " + name + ";");
        }
    }

    private static String computeCeleritasHeader() {
        final ShaderConstants constants = ShaderConstants.builder()
            .add("VERT_POS_SCALE", "1.0")
            .add("VERT_POS_OFFSET", "0.0")
            .add("VERT_TEX_SCALE", "1.0")
            .build();

        final String chunkVertexHeader = org.embeddedt.embeddium.impl.gl.shader.ShaderParser.parseShader(
            ShaderLoader.getShaderSource("sodium:include/chunk_vertex.glsl"), ShaderLoader::getShaderSource, constants)
            .replace("_get_relative_chunk_coord(pos) * vec3(16.0)", "vec3(_get_relative_chunk_coord(pos)) * 16.0");


        return "\n\n" + chunkVertexHeader + "\n\n";
    }

    public static String getFormattedShader(ParseTree tree, String string) {
        final StringBuilder sb = new StringBuilder(string + "\n");
        final String[] tabHolder = {""};
        getFormattedShader(tree, sb, tabHolder);
        return sb.toString();
    }

    private static void getFormattedShader(ParseTree tree, StringBuilder stringBuilder, String[] tabHolder) {
        if (tree instanceof TerminalNode) {
            final String text = tree.getText();
            if (text.equals("<EOF>")) {
                return;
            }
            if (text.equals("#")) {
                stringBuilder.append("\n#");
                return;
            }
            stringBuilder.append(text);
            if (text.equals("{")) {
                stringBuilder.append(" \n\t");
                tabHolder[0] = "\t";
            }

            if (text.equals("}")) {
                if (stringBuilder.length() >= 2) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                }
                tabHolder[0] = "";
            }
            stringBuilder.append(text.equals(";") ? " \n" + tabHolder[0] : " ");
        } else {
            for (int i = 0; i < tree.getChildCount(); ++i) {
                getFormattedShader(tree.getChild(i), stringBuilder, tabHolder);
            }
        }
    }

}
