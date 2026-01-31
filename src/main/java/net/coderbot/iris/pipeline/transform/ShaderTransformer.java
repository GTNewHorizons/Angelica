package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.StorageCollector;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import com.gtnewhorizons.angelica.glsm.RenderSystem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderTransformer {
    private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");
    private static final Pattern inOutVaryingPattern = Pattern.compile("(?m)^(\\s*(?:(?:flat|smooth|noperspective)\\s+)?)(in|out)(\\s+)");
    private static final Pattern inPattern = Pattern.compile("(?m)^(\\s*(?:(?:flat|smooth|noperspective)\\s+)?)(in)(\\s+)");
    private static final Pattern outPattern = Pattern.compile("(?m)^(\\s*(?:(?:flat|smooth|noperspective)\\s+)?)(out)(\\s+)");
    private static final Pattern texturePattern = Pattern.compile("\\btexture\\s*\\(|(\\btexture\\b)");
    private static final Pattern unsignedSuffixPattern = Pattern.compile("(\\b(?:\\d+|0[xX][0-9a-fA-F]+))[uU]\\b");

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

    private static final Map<Integer, List<String>> versionedReservedWords = new HashMap<>();

    private static String replaceTexture(String input) {
        final var matcher = texturePattern.matcher(input);
        final StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                matcher.appendReplacement(builder, "iris_renamed_texture");
            } else {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    static {
        // sample was added as a keyword in GLSL 400, many shaders use it
        versionedReservedWords.put(400, List.of("sample"));
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


    private record DowngradeRule(
        int fromVersion,
        int toVersion,
        BooleanSupplier supported,
        List<String> extensions,
        Map<String, String> defines,
        List<String> preprocessorDefines,
        boolean convertStorageQualifiers
    ) {}

    private static final DowngradeRule[] DOWNGRADE_RULES = {
        new DowngradeRule(130, 120,
            RenderSystem::supportsGpuShader4,
            List.of("GL_EXT_gpu_shader4"),
            Map.of("texture", "texture2D"),
            List.of(
                // Preprocessor defines
                "#define uint int",
                "#define uvec2 ivec2",
                "#define uvec3 ivec3",
                "#define uvec4 ivec4",
                "#define isnan(x) ((x) != (x))",
                "#define isinf(x) ((x) == (1.0/0.0) || (x) == (-1.0/0.0))",
                "#define trunc(x) (sign(x) * floor(abs(x)))",
                "#define round(x) (floor((x) + 0.5))",
                "#define texelFetch texelFetch2D",
                "#define textureSize textureSize2D",
                "#define modf iris_modf",
                // Function definitions
                "GLSL_FUNC:float iris_modf(float x, out float i) { i = sign(x) * floor(abs(x)); return x - i; }",
                "GLSL_FUNC:vec2 iris_modf(vec2 x, out vec2 i) { i = sign(x) * floor(abs(x)); return x - i; }",
                "GLSL_FUNC:vec3 iris_modf(vec3 x, out vec3 i) { i = sign(x) * floor(abs(x)); return x - i; }",
                "GLSL_FUNC:vec4 iris_modf(vec4 x, out vec4 i) { i = sign(x) * floor(abs(x)); return x - i; }",
                "GLSL_FUNC:vec2 mix(vec2 a, vec2 b, bvec2 sel) { return vec2(sel.x ? b.x : a.x, sel.y ? b.y : a.y); }",
                "GLSL_FUNC:vec3 mix(vec3 a, vec3 b, bvec3 sel) { return vec3(sel.x ? b.x : a.x, sel.y ? b.y : a.y, sel.z ? b.z : a.z); }",
                "GLSL_FUNC:vec4 mix(vec4 a, vec4 b, bvec4 sel) { return vec4(sel.x ? b.x : a.x, sel.y ? b.y : a.y, sel.z ? b.z : a.z, sel.w ? b.w : a.w); }",
                "GLSL_FUNC:bool any(bool b) { return b; }",
                "GLSL_FUNC:bool all(bool b) { return b; }"
            ),
            true
        )
    };

    record NegotiationResult(
        int targetVersion,
        String profile,
        List<String> extensions,
        Map<String, String> defines,
        List<String> preprocessorDefines,
        boolean convertStorageQualifiers,
        String error
    ) {
        static NegotiationResult success(int targetVersion, String profile, List<String> extensions, Map<String, String> defines, List<String> preprocessorDefines, boolean convertStorageQualifiers) {
            return new NegotiationResult(targetVersion, profile, extensions, defines, preprocessorDefines, convertStorageQualifiers, null);
        }

        static NegotiationResult error(String message) {
            return new NegotiationResult(-1, "", List.of(), Map.of(), List.of(), false, message);
        }

        static NegotiationResult noop(int version, String profile) {
            return new NegotiationResult(version, profile, List.of(), Map.of(), List.of(), false, null);
        }

        boolean isError() { return error != null; }
    }

    private static int getStageMinimumVersion(PatchShaderType stage) {
        return switch (stage) {
            case COMPUTE -> 330;
            case TESS_CONTROL, TESS_EVAL -> 400;
            case GEOMETRY -> 150;
            default -> 110;
        };
    }

    static NegotiationResult negotiateVersion(int effectiveVersion, PatchShaderType stage) {
        final int maxGlsl = RenderSystem.getMaxGlslVersion();

        if (effectiveVersion <= maxGlsl) {
            if (effectiveVersion <= 120 && RenderSystem.supportsGpuShader4()) {
                final String profile = "";
                for (DowngradeRule rule : DOWNGRADE_RULES) {
                    if (rule.fromVersion == 130 && rule.toVersion == 120) {
                        final List<String> definesOnly = rule.preprocessorDefines.stream().filter(d -> !d.startsWith("GLSL_FUNC:")).toList();
                        return NegotiationResult.success(effectiveVersion, profile, rule.extensions, rule.defines, definesOnly, true);
                    }
                }
                return NegotiationResult.success(effectiveVersion, profile, List.of("GL_EXT_gpu_shader4"), Map.of(), List.of(), true);
            }
            return NegotiationResult.noop(effectiveVersion, effectiveVersion >= 150 ? "compatibility" : "");
        }

        final int stageMin = getStageMinimumVersion(stage);
        if (maxGlsl < stageMin) {
            return NegotiationResult.error("Hardware GLSL " + maxGlsl + " below stage minimum " + stageMin + " for " + stage.name());
        }

        // Walk downgrade rules from effectiveVersion toward maxGlsl
        final List<String> accExtensions = new ArrayList<>();
        final Map<String, String> accDefines = new HashMap<>();
        final List<String> accPreprocessorDefines = new ArrayList<>();
        boolean accConvertQualifiers = false;
        int currentVersion = effectiveVersion;

        while (currentVersion > maxGlsl) {
            DowngradeRule matched = null;
            for (DowngradeRule rule : DOWNGRADE_RULES) {
                if (rule.fromVersion == currentVersion) {
                    matched = rule;
                    break;
                }
            }

            if (matched == null) {
                return NegotiationResult.error("No downgrade rule from GLSL " + currentVersion + " (hardware max: " + maxGlsl + ", shader requires: " + effectiveVersion + ")");
            }

            // Verify rule's required extensions are supported
            if (!matched.supported.getAsBoolean()) {
                return NegotiationResult.error("Downgrade from " + matched.fromVersion + " to " + matched.toVersion + " requires extensions " + matched.extensions + " which are not supported");
            }

            accExtensions.addAll(matched.extensions);
            accDefines.putAll(matched.defines);
            accPreprocessorDefines.addAll(matched.preprocessorDefines);
            accConvertQualifiers |= matched.convertStorageQualifiers;
            currentVersion = matched.toVersion;
        }

        if (currentVersion < stageMin) {
            return NegotiationResult.error("Downgrade reached GLSL " + currentVersion + " which is below stage minimum " + stageMin + " for " + stage.name());
        }

        final String profile = currentVersion >= 150 ? "compatibility" : "";
        return NegotiationResult.success(currentVersion, profile, accExtensions, accDefines, accPreprocessorDefines, accConvertQualifiers);
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

        String profileString = "#version " + versionString + " core\n";

        // Rename reserved words
        String input = replaceTexture(compute);
        for (int version : versionedReservedWords.keySet()) {
            if (versionInt < version) {
                for (String reservedWord : versionedReservedWords.get(version)) {
                    final String newName = "iris_renamed_" + reservedWord;
                    input = input.replaceAll("\\b" + reservedWord + "\\b", newName);
                }
            }
        }

        final var parsedShader = ShaderParser.parseShader(input);
        final var transformer = new Transformer(parsedShader.full());

        doTransform(transformer, patchType, parameters, "core", versionInt);

        // Extract extensions
        final var extensions = versionPattern.matcher(getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

        final String finalHeader = profileString + (extensions.isEmpty() ? "" : "\n" + extensions);
        final StringBuilder formattedShaderBuilder = new StringBuilder();

        transformer.mutateTree(tree -> {
            formattedShaderBuilder.append(getFormattedShader(tree, finalHeader));
        });

        String formattedShader = formattedShaderBuilder.toString();

        // Restore identifiers that were temporarily renamed
        formattedShader = formattedShader.replace("iris_renamed_texture", "texture");
        formattedShader = formattedShader.replace("iris_renamed_sample", "sample");

        result.put(PatchShaderType.COMPUTE, formattedShader);

        watch.stop();
        Iris.logger.info("[Load #{}] Transformed compute shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
         final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, NegotiationResult> negotiations = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, Boolean> needsTextureLodExtension = new EnumMap<>(PatchShaderType.class);

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

            String profile = "";
            int versionInt = Integer.parseInt(versionString);

            // Include celeritas header in scan — it's injected post-negotiation but contains uint/uvec3
            final String scanSource = (patchType == Patch.CELERITAS_TERRAIN && type == PatchShaderType.VERTEX) ? input + computeCeleritasHeader() : input;
            final int requiredVersion = getRequiredVersion(scanSource, versionInt);
            if (requiredVersion > versionInt) {
                Iris.logger.debug("Shader requires GLSL {} for detected features, hoisting from {}", requiredVersion, versionInt);
                versionInt = requiredVersion;
                versionString = String.valueOf(versionInt);
            }

            // Negotiate version downgrade if needed
            final NegotiationResult negotiation = negotiateVersion(versionInt, type);
            if (negotiation.isError()) {
                throw new RuntimeException("Shader version negotiation failed for " + type.name() + ": " + negotiation.error());
            }

            if (negotiation.targetVersion() != versionInt) {
                String negotiationKey = type.name() + "_" + versionInt + "_" + negotiation.targetVersion();
                if (loggedNegotiations.add(negotiationKey)) {
                    Iris.logger.info("Negotiated {} shader from GLSL {} to {} (extensions: {}, polyfills: {})", type.name(), versionInt, negotiation.targetVersion(), negotiation.extensions(), negotiation.preprocessorDefines().size());
                }
                versionInt = negotiation.targetVersion();
                versionString = String.valueOf(versionInt);
                profile = negotiation.profile();
            } else {
                if (versionInt >= 150) {
                    profile = matcher.group(2);
                    if (profile == null) {
                        profile = "compatibility";
                    }
                }
            }
            negotiations.put(type, negotiation);

            final String profileString = "#version " + versionString + (profile.isEmpty() ? "" : " " + profile) + "\n";

            input = replaceTexture(input);
            // The primary reason we rename words here using regex, is because if the words cause invalid
            // GLSL, regardless of the version being used, it will cause glsl-transformation-lib to fail
            // so we need to rename them prior to passing the shader input to glsl-transformation-lib.
            for (int version : versionedReservedWords.keySet()) {
                if (versionInt < version) {
                    for (String reservedWord : versionedReservedWords.get(version)) {
                        final  String newName = "iris_renamed_" + reservedWord;
                        input = input.replaceAll("\\b" + reservedWord + "\\b", newName);
                    }
                }
            }

            final var parsedShader = ShaderParser.parseShader(input);
            final var transformer = new Transformer(parsedShader.full());

            if (parameters.type == ShaderType.VERTEX || parameters.type == ShaderType.FRAGMENT) {
                upgradeStorageQualifiers(transformer, parameters);
            }

            doTransform(transformer, patchType, parameters, profile, versionInt);

            // Check if we need to patch in texture LOD extension enabling
            if (versionInt <= 120 && (transformer.containsCall("texture2DLod") || transformer.containsCall("texture3DLod") || transformer.containsCall("texture2DGradARB"))) {
                needsTextureLodExtension.put(type, true);
            }

            // Extract extensions from the pre-parsed content (version + extensions before main code)
            // This preserves #extension directives that the shader pack declares
            final var extensions = versionPattern.matcher(getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

            types.put(type, transformer);
            prepatched.put(type, profileString + (extensions.isEmpty() ? "" : "\n" + extensions));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        for (var entry : types.entrySet()) {
            final PatchShaderType shaderType = entry.getKey();
            final Transformer transformer = entry.getValue();
            String header = prepatched.get(shaderType);
            final NegotiationResult negotiation = negotiations.get(shaderType);

            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
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

            // Inject texture LOD extension if needed
            if (needsTextureLodExtension.containsKey(shaderType)) {
                final String[] parts = formattedShader.split("\n", 2);
                parts[1] = "#extension GL_ARB_shader_texture_lod : require\n" + parts[1];
                formattedShader = parts[0] + "\n" + parts[1];
            }

            if (negotiation != null && (!negotiation.extensions().isEmpty() || !negotiation.preprocessorDefines().isEmpty())) {
                formattedShader = injectGlslPreamble(formattedShader, negotiation.extensions(), negotiation.preprocessorDefines());
            }

            if (negotiation != null && !negotiation.defines().isEmpty()) {
                for (var defineEntry : negotiation.defines().entrySet()) {
                    formattedShader = formattedShader.replaceAll("\\b" + Pattern.quote(defineEntry.getKey()) + "\\b", Matcher.quoteReplacement(defineEntry.getValue()));
                }
            }

            // Convert storage qualifiers for downgraded shaders or native 120 shaders
            if (negotiation != null && (negotiation.convertStorageQualifiers() || negotiation.targetVersion() <= 120)) {
                if (shaderType == PatchShaderType.VERTEX) {
                    final Matcher inMatcher = inPattern.matcher(formattedShader);
                    formattedShader = inMatcher.replaceAll("$1attribute$3");
                    final Matcher outMatcher = outPattern.matcher(formattedShader);
                    formattedShader = outMatcher.replaceAll("$1varying$3");
                } else {
                    final Matcher inOutVaryingMatcher = inOutVaryingPattern.matcher(formattedShader);
                    formattedShader = inOutVaryingMatcher.replaceAll("$1varying$3");
                }
                formattedShader = unsignedSuffixPattern.matcher(formattedShader).replaceAll("$1");
            }

            result.put(shaderType, formattedShader);
        }
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, String profile, int versionInt) {
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
                AttributeTransformer.transform(transformer, (AttributeParameters) parameters, profile, versionInt);
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

    /**
     * Converts GLSL 120 storage qualifiers (varying/attribute) to modern in/out tokens so downstream AST transforms (e.g. CompatibilityTransformer) can find them.
     * The hacky120Patches regex pass converts them back for the final output.
     */
    public static void upgradeStorageQualifiers(Transformer root, Parameters parameters) {
        final List<TerminalNode> tokens = new ArrayList<>();
        root.mutateTree(tree -> ParseTreeWalker.DEFAULT.walk(new StorageCollector(tokens), tree));

        for (TerminalNode node : tokens) {
            if (!(node.getSymbol() instanceof CommonToken token)) {
                return;
            }
            if (token.getType() == GLSLParser.ATTRIBUTE) {
                token.setType(GLSLParser.IN);
                token.setText(GLSLParser.VOCABULARY.getLiteralName(GLSLParser.IN).replace("'", ""));
            } else if (token.getType() == GLSLParser.VARYING) {
                if (parameters.type == ShaderType.VERTEX) {
                    token.setType(GLSLParser.OUT);
                    token.setText(GLSLParser.VOCABULARY.getLiteralName(GLSLParser.OUT).replace("'", ""));
                } else {
                    token.setType(GLSLParser.IN);
                    token.setText(GLSLParser.VOCABULARY.getLiteralName(GLSLParser.IN).replace("'", ""));
                }
            }
        }
    }

    private static String injectGlslPreamble(String shader, List<String> extensions, List<String> preprocessorDefines) {
        final StringBuilder extensionBlock = new StringBuilder();
        final StringBuilder defineBlock = new StringBuilder();
        final StringBuilder functionBlock = new StringBuilder();

        for (String ext : extensions) {
            extensionBlock.append("#extension ").append(ext).append(" : require\n");
        }
        for (String define : preprocessorDefines) {
            if (define.startsWith("GLSL_FUNC:")) {
                functionBlock.append(define.substring(10)).append("\n");
            } else {
                defineBlock.append(define).append("\n");
            }
        }

        // Find where preprocessor section ends and code begins
        final String[] lines = shader.split("\n");
        final StringBuilder shaderResult = new StringBuilder();
        boolean extensionsInjected = false, definesInjected = false, functionsInjected = false;

        for (String line : lines) {
            final String trimmed = line.trim();
            final boolean isPreprocessor = trimmed.startsWith("#");
            final boolean isExtension = trimmed.startsWith("#extension");
            final boolean isVersion = trimmed.startsWith("#version");

            // Inject our extensions right after #version
            if (isVersion) {
                shaderResult.append(line).append("\n");
                shaderResult.append(extensionBlock);
                extensionsInjected = true;
                continue;
            }

            // Inject our defines after the last #extension line (before other preprocessor or code)
            if (!definesInjected && extensionsInjected && !isExtension && !trimmed.isEmpty()) {
                shaderResult.append(defineBlock);
                definesInjected = true;
            }

            // Inject our functions after all preprocessor lines
            if (!functionsInjected && definesInjected && !isPreprocessor && !trimmed.isEmpty()) {
                shaderResult.append(functionBlock);
                functionsInjected = true;
            }

            shaderResult.append(line).append("\n");
        }

        if (!definesInjected) shaderResult.append(defineBlock);
        if (!functionsInjected) shaderResult.append(functionBlock);

        return shaderResult.toString();
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
                stringBuilder.append(" \n");
            } else {
                stringBuilder.append(text.equals(";") ? " \n" + tabHolder[0] : " ");
            }
        } else {
            for (int i = 0; i < tree.getChildCount(); ++i) {
                getFormattedShader(tree.getChild(i), stringBuilder, tabHolder);
            }
        }
    }

}
