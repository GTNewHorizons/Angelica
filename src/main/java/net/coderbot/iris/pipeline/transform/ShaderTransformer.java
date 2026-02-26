package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderTransformer {
    private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");

    private static final int CACHE_SIZE = 100;
    private static final Object2ObjectLinkedOpenHashMap<TransformKey<?>, Map<PatchShaderType, String>> shaderTransformationCache = new Object2ObjectLinkedOpenHashMap<>();
    private static final boolean useCache = true;

    // Track logged negotiations to avoid spam - cleared on shader pack reload
    private static final Set<String> loggedNegotiations = new HashSet<>();

    public static void clearCache() {
        synchronized (shaderTransformationCache) {
            shaderTransformationCache.clear();
        }
        loggedNegotiations.clear();
    }


    private record VersionRequirement(String keyword, int minVersion, BooleanSupplier supported) {}

    // Sorted descending by minVersion for early exit in getRequiredVersion

    private static final VersionRequirement[] VERSION_REQUIREMENTS = {
        new VersionRequirement("std430", 430, RenderSystem::supportsSSBO),
        new VersionRequirement("iimage", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("uimage", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageLoad", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageStore", 420, RenderSystem::supportsImageLoadStore),

        new VersionRequirement("uint", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec2", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec3", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec4", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("flat", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
    };


    record NegotiationResult(int targetVersion, String profile, String error) {
        static NegotiationResult error(String message) {
            return new NegotiationResult(-1, "", message);
        }

        static NegotiationResult noop(int version, String profile) {
            return new NegotiationResult(version, profile, null);
        }

        boolean isError() { return error != null; }
    }

    private static int getStageMinimumVersion(PatchShaderType stage) {
        return switch (stage) {
            case COMPUTE -> 330;
            case TESS_CONTROL, TESS_EVAL -> 400;
            case GEOMETRY -> 330;
            default -> 330;
        };
    }

    static NegotiationResult negotiateVersion(int effectiveVersion, PatchShaderType stage) {
        final int maxGlsl = RenderSystem.getMaxGlslVersion();

        if (effectiveVersion <= maxGlsl) {
            return NegotiationResult.noop(effectiveVersion, effectiveVersion >= 150 ? "core" : "");
        }

        final int stageMin = getStageMinimumVersion(stage);
        if (maxGlsl < stageMin) {
            return NegotiationResult.error("Hardware GLSL " + maxGlsl + " below stage minimum " + stageMin + " for " + stage.name());
        }

        return NegotiationResult.error("Shader requires GLSL " + effectiveVersion + " but hardware max is " + maxGlsl);
    }

    private static Pattern hoistPattern;
    private static Object2IntMap<String> keywordToVersion;
    private static int maxSupportedHoistVersion;

    public static void init() {
        final StringBuilder patternBuilder = new StringBuilder();
        final Object2IntOpenHashMap<String> versionMap = new Object2IntOpenHashMap<>();
        int maxVersion = 0;

        for (VersionRequirement req : VERSION_REQUIREMENTS) {
            if (req.supported.getAsBoolean()) {
                if (!patternBuilder.isEmpty()) patternBuilder.append('|');

                patternBuilder.append("\\b").append(Pattern.quote(req.keyword)).append("\\b");
                versionMap.put(req.keyword, req.minVersion);
                maxVersion = Math.max(maxVersion, req.minVersion);
            }
        }

        if (!patternBuilder.isEmpty()) {
            hoistPattern = Pattern.compile(patternBuilder.toString());
            keywordToVersion = versionMap;
        }
        maxSupportedHoistVersion = maxVersion;

        Iris.logger.info("Shader version hoisting: {} feature(s) GLSL {}", versionMap.size(), maxVersion > 0 ? maxVersion : "N/A");
    }

    private static int getRequiredVersion(String shaderSource, int declaredVersion) {
        if (hoistPattern == null || declaredVersion >= maxSupportedHoistVersion) {
            return declaredVersion;
        }

        final Matcher m = hoistPattern.matcher(shaderSource);
        int required = declaredVersion;
        while (m.find()) {
            final int ver = keywordToVersion.getInt(m.group());
            if (ver > required) {
                required = ver;
                if (required >= maxSupportedHoistVersion) break;
            }
        }
        return required;
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

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        } else {
            Map<PatchShaderType, String> result;

            var patchType = parameters.patch;

            EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
            inputs.put(PatchShaderType.VERTEX, vertex);
            inputs.put(PatchShaderType.GEOMETRY, geometry);
            inputs.put(PatchShaderType.TESS_CONTROL, tessControl);
            inputs.put(PatchShaderType.TESS_EVAL, tessEval);
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

    public static <P extends Parameters> Map<PatchShaderType, String> transformCompute(String compute, P parameters) {
        if (compute == null) {
            return null;
        } else {
            Map<PatchShaderType, String> result;

            final var patchType = parameters.patch;

            EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
            inputs.put(PatchShaderType.COMPUTE, compute);

            final var key = new TransformKey<>(patchType, inputs, parameters);

            synchronized (shaderTransformationCache) {
                result = shaderTransformationCache.getAndMoveToLast(key);
            }
            if (result == null || !useCache) {
                result = transformComputeInternal(compute, patchType, parameters);
                // Clear this, we don't want whatever random type was last transformed being considered for the key
                parameters.type = null;
                synchronized (shaderTransformationCache) {
                    // Double-check in case another thread added it while we were transforming
                    final Map<PatchShaderType, String> existing = shaderTransformationCache.getAndMoveToLast(key);
                    if (existing != null) {
                        return existing;
                    }
                    if (shaderTransformationCache.size() >= CACHE_SIZE) {
                        shaderTransformationCache.removeFirst();
                    }
                    shaderTransformationCache.putAndMoveToLast(key, result);
                }
            }

            return result;
        }
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformComputeInternal(String compute, Patch patchType, P parameters) {
        final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);

        final Stopwatch watch = Stopwatch.createStarted();

        parameters.type = ShaderType.COMPUTE;

        final Matcher matcher = versionPattern.matcher(compute);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in compute shader source code!");
        }

        String versionString = matcher.group(1);
        int versionInt = Integer.parseInt(versionString);

        // Check if shader uses features requiring a higher GLSL version
        final int requiredVersion = getRequiredVersion(compute, versionInt);
        if (requiredVersion > versionInt) {
            Iris.logger.debug("Compute shader requires GLSL {} for detected features, hoisting from {}", requiredVersion, versionInt);
            versionInt = requiredVersion;
            versionString = String.valueOf(versionInt);
        }

        // Compute shaders always use core profile, minimum 330
        if (versionInt < 330) {
            versionString = "330";
            versionInt = 330;
        }

        // Negotiate version downgrade if needed
        final NegotiationResult negotiation = negotiateVersion(versionInt, PatchShaderType.COMPUTE);
        if (negotiation.isError()) {
            throw new RuntimeException("Compute shader version negotiation failed: " + negotiation.error());
        }
        if (negotiation.targetVersion() != versionInt) {
            Iris.logger.debug("Negotiated compute shader from GLSL {} to {}", versionInt, negotiation.targetVersion());
            versionInt = negotiation.targetVersion();
            versionString = String.valueOf(versionInt);
        }

        final String profileString = "#version " + versionString + " core\n";

        // Pre-parse reserved word renaming
        String input = GlslTransformUtils.replaceTexture(compute);
        input = GlslTransformUtils.renameReservedWords(input, versionInt);

        final var parsedShader = ShaderParser.parseShader(input);
        final var transformer = new Transformer(parsedShader.full());

        doTransform(transformer, patchType, parameters, versionInt);

        // Extract extensions
        final var extensions = versionPattern.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

        final String finalHeader = profileString + (extensions.isEmpty() ? "" : "\n" + extensions);
        final StringBuilder formattedShaderBuilder = new StringBuilder();

        transformer.mutateTree(tree -> formattedShaderBuilder.append(GlslTransformUtils.getFormattedShader(tree, finalHeader)));

        String formattedShader = GlslTransformUtils.restoreReservedWords(formattedShaderBuilder.toString());

        result.put(PatchShaderType.COMPUTE, formattedShader);

        watch.stop();
        Iris.logger.info("[Load #{}] Transformed compute shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
         final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);

        final Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.VALUES) {
            parameters.type = type.glShaderType;
            if (inputs.get(type) == null) {
                continue;
            }

            String input = inputs.get(type);

            final Matcher matcher = versionPattern.matcher(input);
            if (!matcher.find()) {
                throw new IllegalArgumentException("No #version directive found in source code!");
            }

            String versionString = matcher.group(1);
            if (versionString == null) {
                continue;
            }

            int versionInt = Integer.parseInt(versionString);

            // Include celeritas header in scan — it's injected post-negotiation but contains uint/uvec3
            final String scanSource = (patchType == Patch.CELERITAS_TERRAIN && type == PatchShaderType.VERTEX) ? input + computeCeleritasHeader() : input;
            final int requiredVersion = getRequiredVersion(scanSource, versionInt);
            if (requiredVersion > versionInt) {
                Iris.logger.debug("Shader requires GLSL {} for detected features, hoisting from {}", requiredVersion, versionInt);
                versionInt = requiredVersion;
                versionString = String.valueOf(versionInt);
            }

            // Ensure minimum version for this stage (330 for most, 400 for tessellation)
            final int stageMin = getStageMinimumVersion(type);
            if (versionInt < stageMin) {
                versionInt = stageMin;
                versionString = String.valueOf(versionInt);
            }

            // Negotiate version if needed (error if hardware can't support)
            final NegotiationResult negotiation = negotiateVersion(versionInt, type);
            if (negotiation.isError()) {
                throw new RuntimeException("Shader version negotiation failed for " + type.name() + ": " + negotiation.error());
            }

            // All stages >= 330 use core profile
            final String profile = "core";
            final String profileString = "#version " + versionString + " " + profile + "\n";

            // Pre-parse reserved word renaming — prevents ANTLR parse failures
            input = GlslTransformUtils.replaceTexture(input);
            input = GlslTransformUtils.renameReservedWords(input, versionInt);
            input = CompatShaderTransformer.fixupQualifiers(input, parameters.type == ShaderType.FRAGMENT);

            final var parsedShader = ShaderParser.parseShader(input);
            final var transformer = new Transformer(parsedShader.full());

            doTransform(transformer, patchType, parameters, versionInt);

            // Extract extensions from the pre-parsed content (version + extensions before main code)
            // This preserves #extension directives that the shader pack declares
            final var extensions = versionPattern.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

            types.put(type, transformer);
            prepatched.put(type, profileString + (extensions.isEmpty() ? "" : "\n" + extensions));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            final PatchShaderType shaderType = entry.getKey();
            final Transformer transformer = entry.getValue();
            String header = prepatched.get(shaderType);

            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                header += computeCeleritasHeader();
            }

            final String finalHeader = header;
            final StringBuilder formattedShaderBuilder = new StringBuilder();

            transformer.mutateTree(tree -> formattedShaderBuilder.append(GlslTransformUtils.getFormattedShader(tree, finalHeader)));

            String formattedShader = GlslTransformUtils.restoreReservedWords(formattedShaderBuilder.toString());

            result.put(shaderType, formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case CELERITAS_TERRAIN:
                CeleritasTransformer.transform(transformer, parameters, versionInt);
                // Handle mc_midTexCoord for Celeritas
                patchMultiTexCoord3(transformer, parameters);
                replaceMidTexCoord(transformer, IrisExtendedChunkVertexType.MID_TEX_SCALE);
                applyIntelHd4000Workaround(transformer);
                break;
            case COMPOSITE:
                CompositeDepthTransformer.transform(transformer, parameters, versionInt);
                break;
            case ATTRIBUTES:
                AttributeTransformer.transform(transformer, (AttributeParameters) parameters, versionInt);
                break;
            case COMPUTE:
                ComputeTransformer.transform(transformer, parameters, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        CompatibilityTransformer.transformEach(transformer, parameters);
    }

    public static void applyIntelHd4000Workaround(Transformer transformer) {
        transformer.renameFunctionCall("ftransform", "iris_ftransform");
    }

    public static void patchMultiTexCoord3(Transformer transformer, Parameters parameters) {
        if (parameters.type == ShaderType.VERTEX && transformer.hasVariable("gl_MultiTexCoord3") && !transformer.hasVariable("mc_midTexCoord")) {
            transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
            transformer.injectVariable("attribute vec4 mc_midTexCoord;");
        }
    }

    public static void replaceMidTexCoord(Transformer transformer, float textureScale) {
        final int type = transformer.findType("mc_midTexCoord");
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


}
