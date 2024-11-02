package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.Util;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

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
    static String tab = "";

    private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");

    private static final int CACHE_SIZE = 100;
    private static final Object2ObjectLinkedOpenHashMap<TransformKey, Map<PatchShaderType, String>> shaderTransformationCache = new Object2ObjectLinkedOpenHashMap<>();
    private static final boolean useCache = true;

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

        public Patch patchType() {
            return patchType;
        }

        public EnumMap<PatchShaderType, String> inputs() {
            return inputs;
        }

        public P params() {
            return params;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TransformKey) obj;
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

            var key = new TransformKey(patchType, inputs, parameters);

            result = shaderTransformationCache.getAndMoveToFirst(key);
            if(result == null || !useCache) {
                result = transformInternal(inputs, patchType, parameters);
                // Clear this, we don't want whatever random type was last transformed being considered for the key
                parameters.type = null;
                if(shaderTransformationCache.size() >= CACHE_SIZE) {
                    shaderTransformationCache.removeLast();
                }
                shaderTransformationCache.putAndMoveToLast(key, result);
            }

            return result;
        }
    }

    private static void configureNoError(Parser parser) {
        parser.setErrorHandler(new BailErrorStrategy());
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    }

    private static void configureError(Parser parser) {
        parser.setErrorHandler(new DefaultErrorStrategy());
        parser.addErrorListener(ConsoleErrorListener.INSTANCE);
        parser.getInterpreter().setPredictionMode(PredictionMode.LL);
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, GLSLParser.Translation_unitContext> types = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);
        List<GLSLParser.Translation_unitContext> textureLodExtensionPatches = new ArrayList<>();

        Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.values()) {
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

            GLSLLexer lexer = new GLSLLexer(CharStreams.fromString(input));
            GLSLParser parser = new GLSLParser(new CommonTokenStream(lexer));
            parser.setBuildParseTree(true);
            configureNoError(parser);

            GLSLParser.Translation_unitContext translationUnit;
            try {
                translationUnit = doTransform(parser, patchType, parameters, profile, versionInt);
            } catch (Exception e) {
                lexer.reset();
                parser.reset();
                configureError(parser);
                translationUnit = doTransform(parser, patchType, parameters, profile, versionInt);
            }

            // Check if we need to patch in texture LOD extension enabling
            if (versionInt <= 120 && (Util.containsCall(translationUnit, "texture2DLod") || Util.containsCall(translationUnit, "texture3DLod"))) {
                textureLodExtensionPatches.add(translationUnit);
            }
            types.put(type, translationUnit);
            prepatched.put(type, profileString);
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            // This is a hack to inject an extension declaration when the GLSL version is less than 120
            // Doing this as a string manipulation on the final shader output since it needs to put this
            // before variables, didn't see an easy way to do that with glsl-transformation-lib
            // Eventually this can probably be moved to use glsl-transformation-lib
            String formattedShader = getFormattedShader(entry.getValue(), prepatched.get(entry.getKey()));
            if (textureLodExtensionPatches.contains(entry.getValue())) {
                String[] parts = formattedShader.split("\n", 2);
                parts[1] = "#extension GL_ARB_shader_texture_lod : require\n" + parts[1];
                formattedShader = parts[0] + "\n" + parts[1];
            }
            result.put(entry.getKey(), formattedShader);
        }
        watch.stop();
        Iris.logger.info("Transformed shader for {} in {}", patchType.name(), watch);
        return result;
    }

    private static GLSLParser.Translation_unitContext doTransform(GLSLParser parser, Patch patchType, Parameters parameters, String profile, int versionInt) {
        GLSLParser.Translation_unitContext translationUnit = parser.translation_unit();
        switch (patchType) {
            case SODIUM_TERRAIN:
                SodiumTransformer.transform(translationUnit, parameters);
                break;
            case COMPOSITE:
                CompositeDepthTransformer.transform(translationUnit);
                break;
            case ATTRIBUTES:
                AttributeTransformer.transform(translationUnit, (AttributeParameters) parameters, profile, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        CompatibilityTransformer.transformEach(translationUnit, parameters);
        return translationUnit;
    }

    public static void applyIntelHd4000Workaround(GLSLParser.Translation_unitContext translationUnit) {
        Util.renameFunctionCall(translationUnit, "ftransform", "iris_ftransform");
    }


    public static void replaceGlMultiTexCoordBounded(GLSLParser.Translation_unitContext translationUnit, int min, int max) {
        for (int i = min; i <= max; i++) {
            Util.replaceExpression(translationUnit, "gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
        }
    }

    public static void patchMultiTexCoord3(GLSLParser.Translation_unitContext translationUnit, Parameters parameters) {
        if (parameters.type.glShaderType == ShaderType.VERTEX && Util.hasVariable(translationUnit, "gl_MultiTexCoord3") && !Util.hasVariable(translationUnit, "mc_midTexCoord")) {
            Util.rename(translationUnit, "gl_MultiTexCoord3", "mc_midTexCoord");
            Util.injectVariable(translationUnit, "attribute vec4 mc_midTexCoord;");
        }
    }

    public static void replaceMidTexCoord(GLSLParser.Translation_unitContext translationUnit, float textureScale) {
        int type = Util.findType(translationUnit, "mc_midTexCoord");
        if (type != 0) {
            Util.removeVariable(translationUnit, "mc_midTexCoord");
        }
        Util.replaceExpression(translationUnit, "mc_midTexCoord", "iris_MidTex");
        switch (type) {
            case 0:
                return;
            case GLSLLexer.BOOL:
                return;
            case GLSLLexer.FLOAT:
                Util.injectFunction(translationUnit, "float iris_MidTex = (mc_midTexCoord.x * " + textureScale + ").x;"); //TODO go back to variable if order is fixed
                break;
            case GLSLLexer.VEC2:
                Util.injectFunction(translationUnit, "vec2 iris_MidTex = (mc_midTexCoord.xy * " + textureScale + ").xy;");
                break;
            case GLSLLexer.VEC3:
                Util.injectFunction(translationUnit, "vec3 iris_MidTex = vec3(mc_midTexCoord.xy * " + textureScale + ", 0.0);");
                break;
            case GLSLLexer.VEC4:
                Util.injectFunction(translationUnit, "vec4 iris_MidTex = vec4(mc_midTexCoord.xy * " + textureScale + ", 0.0, 1.0);");
                break;
            default:

        }

        Util.injectVariable(translationUnit, "in vec2 mc_midTexCoord;"); //TODO why is this inserted oddly?

    }

    public static void addIfNotExists(GLSLParser.Translation_unitContext translationUnit, String name, String code) {
        if (!Util.hasVariable(translationUnit, name)) {
            Util.injectVariable(translationUnit, code);
        }
    }

    public static void addIfNotExistsType(GLSLParser.Translation_unitContext translationUnit, String name, String type) {
        if (!Util.hasVariable(translationUnit, name)) {
            Util.injectVariable(translationUnit, type + " " + name + ";");
        }
    }

    public static String getFormattedShader(ParseTree tree, String string) {
        StringBuilder sb = new StringBuilder(string + "\n");
        getFormattedShader(tree, sb);
        return sb.toString();
    }

    private static void getFormattedShader(ParseTree tree, StringBuilder stringBuilder) {
        if (tree instanceof TerminalNode) {
            String text = tree.getText();
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
                tab = "\t";
            }

            if (text.equals("}")) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                tab = "";
            }
            stringBuilder.append(text.equals(";") ? " \n" + tab : " ");
        } else {
            for(int i = 0; i < tree.getChildCount(); ++i) {
                getFormattedShader(tree.getChild(i), stringBuilder);
            }
        }

    }

}
