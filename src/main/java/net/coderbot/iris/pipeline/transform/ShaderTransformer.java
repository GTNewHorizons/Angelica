package net.coderbot.iris.pipeline.transform;

import com.google.common.base.Stopwatch;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.coderbot.iris.Iris;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.Arrays;
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


    private record VersionRequirement(String keyword, int minVersion, boolean prefix, BooleanSupplier supported) {
        VersionRequirement(String keyword, int minVersion, BooleanSupplier supported) {
            this(keyword, minVersion, false, supported);
        }
    }

    // Sorted descending by minVersion for early exit in getRequiredVersion

    private static final VersionRequirement[] VERSION_REQUIREMENTS = {
        new VersionRequirement("std430", 430, RenderSystem::supportsSSBO),
        new VersionRequirement("iimage", 420, true, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("uimage", 420, true, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageLoad", 420, RenderSystem::supportsImageLoadStore),
        new VersionRequirement("imageStore", 420, RenderSystem::supportsImageLoadStore),

        new VersionRequirement("uint", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec2", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec3", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("uvec4", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
        new VersionRequirement("flat", 130, () -> RenderSystem.getMaxGlslVersion() >= 130),
    };

    static boolean canEmitImageOps() {
        return RenderSystem.supportsImageLoadStore() && RenderSystem.getMaxGlslVersion() >= 420;
    }


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

    private static VersionRequirement[] enabledRequirements;
    private static int maxSupportedHoistVersion;

    public static void init() {
        enabledRequirements = Arrays.stream(VERSION_REQUIREMENTS)
            .filter(req -> req.supported.getAsBoolean())
            .toArray(VersionRequirement[]::new);
        int maxVersion = 0;
        for (VersionRequirement req : enabledRequirements) {
            maxVersion = Math.max(maxVersion, req.minVersion);
        }
        maxSupportedHoistVersion = maxVersion;

        Iris.logger.info("Shader version hoisting: {} feature(s) GLSL {}", enabledRequirements.length, maxVersion > 0 ? maxVersion : "N/A");
    }

    private static int getRequiredVersion(String shaderSource, int declaredVersion) {
        if (enabledRequirements == null || enabledRequirements.length == 0 || declaredVersion >= maxSupportedHoistVersion) {
            return declaredVersion;
        }

        final GLSLLexer lexer = new GLSLLexer(CharStreams.fromString(shaderSource));
        int required = declaredVersion;
        for (Token t = lexer.nextToken(); t.getType() != Token.EOF; t = lexer.nextToken()) {
            final int channel = t.getChannel();
            if (channel == GLSLLexer.COMMENTS || channel == Token.HIDDEN_CHANNEL) continue;
            if (t.getStopIndex() - t.getStartIndex() < 3) continue;
            required = matchRequirement(t.getText(), required);
            if (required >= maxSupportedHoistVersion) break;
        }
        return required;
    }

    private static int getRequiredVersion(GLSLParser.Translation_unitContext tree, String headerTail, int declaredVersion) {
        if (enabledRequirements == null || enabledRequirements.length == 0 || declaredVersion >= maxSupportedHoistVersion) {
            return declaredVersion;
        }
        int required = headerTail.isEmpty() ? declaredVersion : getRequiredVersion(headerTail, declaredVersion);
        if (required >= maxSupportedHoistVersion) return required;
        return scanTree(tree, required);
    }

    private static int scanTree(ParseTree tree, int required) {
        if (tree instanceof TerminalNode terminal) {
            final String text = terminal.getSymbol().getText();
            return (text == null || text.length() < 4) ? required : matchRequirement(text, required);
        }
        for (int i = 0; i < tree.getChildCount() && required < maxSupportedHoistVersion; i++) {
            required = scanTree(tree.getChild(i), required);
        }
        return required;
    }

    private static int matchRequirement(String text, int required) {
        for (VersionRequirement req : enabledRequirements) {
            if (req.minVersion <= required) break;
            if (req.prefix ? text.startsWith(req.keyword) : text.equals(req.keyword)) return req.minVersion;
        }
        return required;
    }

    private record StageHeader(int version, String extensions) {}

    private static String finalizeStage(int version, String headerTail, String body, GLSLParser.Translation_unitContext tree, PatchShaderType stage) {
        final int required = getRequiredVersion(tree, headerTail, version);
        if (required > version) {
            final NegotiationResult negotiation = negotiateVersion(required, stage);
            if (negotiation.isError()) {
                throw new RuntimeException("Shader version negotiation failed for " + stage.name() + " after transformation: " + negotiation.error());
            }
            Iris.logger.debug("Transformed {} requires GLSL {} for injected features, hoisting from {}", stage.name(), required, version);
            version = required;
        }
        return "#version " + version + " core\n" + headerTail + body;
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

    record StageArtifact(GLSLParser.Translation_unitContext tree, int headerLen) {}

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        return transform(vertex, geometry, tessControl, tessEval, fragment, parameters, null);
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters, EnumMap<PatchShaderType, StageArtifact> artifactsOut) {
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
                result = transformInternal(inputs, patchType, parameters, artifactsOut);
                // Clear this, we don't want whatever random type was last transformed being considered for the key
                parameters.type = null;
                synchronized (shaderTransformationCache) {
                    // Double-check in case another thread added it while we were transforming
                    Map<PatchShaderType, String> existing = shaderTransformationCache.getAndMoveToLast(key);
                    if (existing != null) {
                        if (artifactsOut != null) artifactsOut.clear();
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
        return transformCompute(compute, parameters, null);
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transformCompute(String compute, P parameters, EnumMap<PatchShaderType, StageArtifact> artifactsOut) {
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
                result = transformComputeInternal(compute, patchType, parameters, artifactsOut);
                // Clear this, we don't want whatever random type was last transformed being considered for the key
                parameters.type = null;
                synchronized (shaderTransformationCache) {
                    // Double-check in case another thread added it while we were transforming
                    final Map<PatchShaderType, String> existing = shaderTransformationCache.getAndMoveToLast(key);
                    if (existing != null) {
                        if (artifactsOut != null) artifactsOut.clear();
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

    private static <P extends Parameters> Map<PatchShaderType, String> transformComputeInternal(String compute, Patch patchType, P parameters, EnumMap<PatchShaderType, StageArtifact> artifactsOut) {
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

        // Pre-parse reserved word renaming
        String input = GlslTransformUtils.replaceTexture(compute);
        input = GlslTransformUtils.renameReservedWords(input, versionInt);

        final var parsedShader = ShaderParser.parseShader(input);
        final var transformer = new Transformer(parsedShader.full());

        doTransform(transformer, patchType, parameters, versionInt);

        // SDL_GPU forbids sampler usage on integer formats, so an integer sampler has to become a storage image here
        final EnumMap<PatchShaderType, Transformer> computeTypes = new EnumMap<>(PatchShaderType.class);
        computeTypes.put(PatchShaderType.COMPUTE, transformer);
        if (canEmitImageOps()) {
            SamplerToStorageImageRewriter.transformGrouped(computeTypes, parameters);
        }
        if (BackendManager.RENDER_BACKEND.isSDLGPU()) {
            LocalZeroInitTransformer.transformGrouped(computeTypes);
        }

        // Extract extensions
        final var extensions = versionPattern.matcher(GlslTransformUtils.getFormattedShader(parsedShader.pre(), "")).replaceFirst("").trim();

        final String headerTail = extensions.isEmpty() ? "" : "\n" + extensions;
        final StringBuilder formattedShaderBuilder = new StringBuilder();
        final GLSLParser.Translation_unitContext[] treeHolder = new GLSLParser.Translation_unitContext[1];

        transformer.mutateTree(tree -> {
            GlslTransformUtils.restoreReservedWordsInTree(tree);
            treeHolder[0] = tree;
            formattedShaderBuilder.append(GlslTransformUtils.getFormattedShaderRebased(tree, ""));
        });

        final String printedBody = formattedShaderBuilder.toString();
        final String formattedShader = finalizeStage(versionInt, headerTail, printedBody, treeHolder[0], PatchShaderType.COMPUTE);

        result.put(PatchShaderType.COMPUTE, formattedShader);
        if (artifactsOut != null) {
            artifactsOut.put(PatchShaderType.COMPUTE, new StageArtifact(treeHolder[0], formattedShader.length() - printedBody.length()));
        }

        watch.stop();
        Iris.logger.info("[Load #{}] Transformed compute shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters, EnumMap<PatchShaderType, StageArtifact> artifactsOut) {
         final EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
         final EnumMap<PatchShaderType, StageHeader> prepatched = new EnumMap<>(PatchShaderType.class);

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

            final int requiredVersion = getRequiredVersion(input, versionInt);
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
            prepatched.put(type, new StageHeader(versionInt, extensions.isEmpty() ? "" : "\n" + extensions));
        }
        CompatibilityTransformer.transformGrouped(types, parameters);
        if (BackendManager.RENDER_BACKEND.isSDLGPU()) {
            SamplerAliasDeduplicator.transformGrouped(types, parameters);
        }
        if (canEmitImageOps()) {
            SamplerToStorageImageRewriter.transformGrouped(types, parameters);
        }
        if (BackendManager.RENDER_BACKEND.isSDLGPU()) {
            LocalZeroInitTransformer.transformGrouped(types);
        }
        for (var entry : types.entrySet()) {
            final PatchShaderType shaderType = entry.getKey();
            final Transformer transformer = entry.getValue();
            final StageHeader stageHeader = prepatched.get(shaderType);

            String headerTail = stageHeader.extensions();
            // For Celeritas terrain vertex shaders, inject chunk_vertex.glsl header
            if (patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                headerTail += computeCeleritasHeader();
            }

            final StringBuilder formattedShaderBuilder = new StringBuilder();
            final GLSLParser.Translation_unitContext[] treeHolder = new GLSLParser.Translation_unitContext[1];

            transformer.mutateTree(tree -> {
                GlslTransformUtils.restoreReservedWordsInTree(tree);
                treeHolder[0] = tree;
                formattedShaderBuilder.append(GlslTransformUtils.getFormattedShaderRebased(tree, ""));
            });

            final String printedBody = formattedShaderBuilder.toString();
            final String formattedShader = finalizeStage(stageHeader.version(), headerTail, printedBody, treeHolder[0], shaderType);

            result.put(shaderType, formattedShader);
            if (artifactsOut != null && !(patchType == Patch.CELERITAS_TERRAIN && shaderType == PatchShaderType.VERTEX)) {
                artifactsOut.put(shaderType, new StageArtifact(treeHolder[0], formattedShader.length() - printedBody.length()));
            }
        }
        maybeExtractRwImageStores(result, patchType);
        watch.stop();
        Iris.logger.info("[Load #{}] Transformed shader for {} in {}", Iris.getShaderPackLoadId(), patchType.name(), watch);
        return result;
    }

    private static void maybeExtractRwImageStores(EnumMap<PatchShaderType, String> result, Patch patchType) {
        if (!BackendManager.RENDER_BACKEND.isSDLGPU()) return;
        if (result.containsKey(PatchShaderType.COMPUTE)) return;

        final String vsh = result.get(PatchShaderType.VERTEX);
        final String fsh = result.get(PatchShaderType.FRAGMENT);
        final RwImageStoreExtractor.Result vshResult = (vsh != null) ? RwImageStoreExtractor.tryExtract(vsh, PatchShaderType.VERTEX, patchType.name()) : null;
        final RwImageStoreExtractor.Result fshResult = (fsh != null) ? RwImageStoreExtractor.tryExtract(fsh, PatchShaderType.FRAGMENT, patchType.name()) : null;

        if (vshResult != null && fshResult != null) {
            throw new RuntimeException("Program " + patchType.name() + " writes images from both VSH and FSH; only one stage may write per program under SDL_GPU");
        }
        if (vshResult != null) {
            result.put(PatchShaderType.VERTEX, vshResult.strippedSource());
            result.put(PatchShaderType.COMPUTE, vshResult.computeSource());
            Iris.logger.info("[RwImageStoreExtractor] Extracted compute pre-pass for {} (mode={}, written={})", patchType.name(), vshResult.mode(), vshResult.writtenImages());
        } else if (fshResult != null) {
            result.put(PatchShaderType.FRAGMENT, fshResult.strippedSource());
            result.put(PatchShaderType.COMPUTE, fshResult.computeSource());
            Iris.logger.info("[RwImageStoreExtractor] Extracted compute pre-pass for {} (mode={}, written={})", patchType.name(), fshResult.mode(), fshResult.writtenImages());
        }
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case CELERITAS_TERRAIN:
                CeleritasTransformer.transform(transformer, parameters, versionInt);
                patchMultiTexCoord3(transformer, parameters);
                replaceMidTexCoord(transformer, IrisExtendedChunkVertexType.MID_TEX_SCALE);
                replaceMCEntity(transformer, parameters);
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
            case DH_TERRAIN:
                DHTerrainTransformer.transform(transformer, parameters, versionInt);
                break;
            case DH_GENERIC:
                DHGenericTransformer.transform(transformer, parameters, versionInt);
                break;
            default:
                throw new IllegalStateException("Unknown patch type: " + patchType.name());
        }
        TextureTransformer.transform(transformer, parameters);
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

    /**
     * Replaces shader-declared mc_Entity (vec2/ivec2/float/int/etc.) with upstream-compatible unpacking
     * from a single uint attribute. The uint is packed as ((blockId + 1) << 1) | (renderType & 1).
     */
    public static void replaceMCEntity(Transformer transformer, Parameters parameters) {
        if (parameters.type != ShaderType.VERTEX) return;

        final int type = transformer.findType("mc_Entity");
        if (type != 0) {
            transformer.removeVariable("mc_Entity");
        }
        transformer.replaceExpression("mc_Entity", "iris_Entity");
        switch (type) {
            case 0:
            case GLSLLexer.BOOL:
                return;
            case GLSLLexer.FLOAT:
                transformer.injectFunction("float iris_Entity = float(int(mc_Entity >> 1u) - 1);");
                break;
            case GLSLLexer.VEC2:
                transformer.injectFunction("vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
                break;
            case GLSLLexer.VEC3:
                transformer.injectFunction("vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
                break;
            case GLSLLexer.VEC4:
                transformer.injectFunction("vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
                break;
            case GLSLLexer.UINT:
                transformer.injectFunction("uint iris_Entity = uint(int(mc_Entity >> 1u) - 1);");
                break;
            case GLSLLexer.INT:
                transformer.injectFunction("int iris_Entity = int(mc_Entity >> 1u) - 1;");
                break;
            case GLSLLexer.IVEC2:
                transformer.injectFunction("ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
                break;
            case GLSLLexer.IVEC3:
                transformer.injectFunction("ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
                break;
            case GLSLLexer.IVEC4:
                transformer.injectFunction("ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
                break;
            default:
                throw new IllegalStateException("Got an invalid format mc_Entity (type token " + type + ").");
        }
        transformer.injectVariable("in uint mc_Entity;");
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
