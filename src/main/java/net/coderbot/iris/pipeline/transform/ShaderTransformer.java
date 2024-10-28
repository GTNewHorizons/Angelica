package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.Util;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLPreParser;

import java.util.AbstractMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class ShaderTransformer {
    static String tab = "";

    private static final int CACHE_SIZE = 100;
    private static final Object2ObjectLinkedOpenHashMap<TransformKey, Map<PatchShaderType, String>> shaderTransformationCache = new Object2ObjectLinkedOpenHashMap<>();
    private static final boolean useCache = true;

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

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, GLSLParser.Translation_unitContext> types = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);

        Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.values()) {
            parameters.type = type;
            if (inputs.get(type) == null) {
                continue;
            }
            GLSLLexer lexer = new GLSLLexer(CharStreams.fromString(inputs.get(type)));
            GLSLPreParser preParser = new GLSLPreParser(new BufferedTokenStream(lexer));
            GLSLParser parser = new GLSLParser(new CommonTokenStream(lexer));
            parser.setBuildParseTree(true);
            var pre = preParser.translation_unit();
            var translationUnit = parser.translation_unit();
            var preparsed = pre.compiler_directive();
            String profile = "";
            String versionString = "0";
            GLSLPreParser.Compiler_directiveContext version = null;
            for (var entry: preparsed) {
                if (entry.version_directive() != null) {
                    version = entry;
                    if (entry.version_directive().number() != null) {
                        versionString = entry.version_directive().number().getText();
                    }
                    if (entry.version_directive().profile() != null) {
                        profile = entry.version_directive().profile().getText();
                    }
                }
            }
            pre.children.remove(version);
            if (versionString == null) {
                continue;
            }

            String profileString = "#version " + versionString + " " + profile;
            int versionInt = Integer.parseInt(versionString);

            switch(patchType) {
                case SODIUM_TERRAIN:
                    SodiumTransformer.transform(translationUnit, parameters);
                    break;
                case COMPOSITE:
                    CompositeTransformer.transform(translationUnit, versionInt);
                    break;
                case ATTRIBUTES:
                    AttributeTransformer.transform(translationUnit, (AttributeParameters) parameters, profile, versionInt);
                    break;
                default:
                    throw new IllegalStateException("Unknown patch type: " + patchType.name());
            }
            CompatibilityTransformer.transformEach(translationUnit, parameters);
            types.put(type, translationUnit);
            prepatched.put(type, getFormattedShader((ParseTree) pre, profileString));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            result.put(entry.getKey(), getFormattedShader(entry.getValue(), prepatched.get(entry.getKey())));
        }
        watch.stop();
        Iris.logger.info("Transformed shader for {} in {}", patchType.name(), watch);
        return result;
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
