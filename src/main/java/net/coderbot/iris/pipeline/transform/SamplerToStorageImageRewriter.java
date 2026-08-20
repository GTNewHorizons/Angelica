package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.image.ImageInformation;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SamplerToStorageImageRewriter {


    public record Candidate(String samplerName, String imageName, String layoutQualifier, int dim, boolean signedInt) {}

    private static volatile Set<Candidate> activeCandidates = Set.of();

    public static void setActiveCandidates(Set<Candidate> candidates) {
        activeCandidates = (candidates == null) ? Set.of() : Set.copyOf(candidates);
    }

    public static Set<Candidate> activeCandidates() {
        return activeCandidates;
    }

    /** SDL_GPU forbids sampler usage on integer formats */
    public static Set<Candidate> buildCandidates(Iterable<ImageInformation> infos) {
        if (!BackendManager.RENDER_BACKEND.isSDLGPU()) return Set.of();
        final HashSet<Candidate> out = new HashSet<>();
        for (ImageInformation info : infos) {
            if (info.samplerName() == null || info.samplerName().isEmpty()) continue;
            if (!info.format().isInteger()) continue;
            final int dim = switch (info.target()) {
                case TEXTURE_1D -> 1;
                case TEXTURE_2D, TEXTURE_RECTANGLE -> 2;
                case TEXTURE_3D -> 3;
            };
            final InternalTextureFormat fmt = info.internalTextureFormat();
            final String layout = fmt.name().toLowerCase(Locale.ROOT);
            final boolean signed = fmt.name().endsWith("I") && !fmt.name().endsWith("UI");
            out.add(new Candidate(info.samplerName(), info.name(), layout, dim, signed));
        }
        return out;
    }

    private SamplerToStorageImageRewriter() {}

    public static void transformGrouped(Map<PatchShaderType, Transformer> trees, Parameters parameters) {
        final Set<Candidate> candidates = activeCandidates;
        if (candidates.isEmpty()) return;

        final Set<String> samplerNames = new HashSet<>();
        for (Candidate c : candidates) samplerNames.add(c.samplerName());
        final Set<String> filtered = collectFilteredSamplers(trees, samplerNames);

        for (Candidate c : candidates) {
            if (filtered.contains(c.samplerName())) continue;
            applyCandidate(trees, c);
        }
    }

    private static void applyCandidate(Map<PatchShaderType, Transformer> trees, Candidate c) {
        final int expectedType = c.signedInt
            ? lexerTypeForISampler(c.dim)
            : lexerTypeForUSampler(c.dim);
        if (expectedType == 0) return;

        for (Transformer t : trees.values()) {
            if (t == null) continue;
            if (t.findType(c.samplerName) != expectedType) continue;
            if (t.findType(c.imageName) != 0) continue;
            t.removeVariable(c.samplerName);
            final String imageType = (c.signedInt ? "iimage" : "uimage") + c.dim + "D";
            t.variable = null;
            t.injectVariable("layout(" + c.layoutQualifier + ") readonly uniform " + imageType + " " + c.imageName + ";");
            t.rename(c.samplerName, c.imageName);
            rewriteTexelFetchToImageLoad(t, c.imageName);
        }
    }

    private static int lexerTypeForUSampler(int dim) {
        return switch (dim) {
            case 1 -> GLSLLexer.USAMPLER1D;
            case 2 -> GLSLLexer.USAMPLER2D;
            case 3 -> GLSLLexer.USAMPLER3D;
            default -> 0;
        };
    }

    private static int lexerTypeForISampler(int dim) {
        return switch (dim) {
            case 1 -> GLSLLexer.ISAMPLER1D;
            case 2 -> GLSLLexer.ISAMPLER2D;
            case 3 -> GLSLLexer.ISAMPLER3D;
            default -> 0;
        };
    }

    private static final Set<String> FILTERED_CALL_NAMES = buildFilteredCallNames();

    private static Set<String> buildFilteredCallNames() {
        final Set<String> modernNames = new HashSet<>(Set.of("texture", "textureLod", "textureGrad", "textureProj", "textureGather", "textureOffset", "textureProjLod"));
        final Set<String> names = new HashSet<>(modernNames);
        GlslTransformUtils.TEXTURE_RENAMES.forEach((legacy, modern) -> {
            if (modernNames.contains(modern)) names.add(legacy);
        });
        return Set.copyOf(names);
    }

    private static Set<String> collectFilteredSamplers(Map<PatchShaderType, Transformer> trees, Set<String> samplerNames) {
        final Set<String> filtered = new HashSet<>();
        if (samplerNames.isEmpty()) return filtered;
        for (Transformer t : trees.values()) {
            if (t == null) continue;
            t.mutateTree(tree -> {
                for (GLSLParser.Postfix_expressionContext expr : GlslAstHelpers.collectAll(tree, GLSLParser.Postfix_expressionContext.class)) {
                    final String fname = GlslAstHelpers.extractCallName(expr);
                    if (fname == null) continue;
                    if (!isFilteredCallName(fname)) continue;
                    final String first = GlslAstHelpers.firstArgIdentifier(expr);
                    if (samplerNames.contains(first)) filtered.add(first);
                }
            });
        }
        return filtered;
    }

    private static boolean isFilteredCallName(String fname) {
        for (String n : FILTERED_CALL_NAMES) if (n.equals(fname)) return true;
        return false;
    }

    private static void rewriteTexelFetchToImageLoad(Transformer transformer, String imageName) {
        transformer.mutateTree(tree -> {
            final List<GLSLParser.Postfix_expressionContext> all = GlslAstHelpers.collectAll(tree, GLSLParser.Postfix_expressionContext.class);
            for (GLSLParser.Postfix_expressionContext expr : all) {
                final String fname = GlslAstHelpers.extractCallName(expr);
                if (!"texelFetch".equals(fname)) continue;
                if (!GlslAstHelpers.firstArgIdentifier(expr).equals(imageName)) continue;
                final String secondArg = GlslAstHelpers.secondArgText(expr);
                if (secondArg == null) continue;
                final String replacement = "imageLoad(" + imageName + ", " + secondArg + ")";
                final GLSLParser.Postfix_expressionContext parsed = ShaderParser.parseSnippet(replacement, GLSLParser::postfix_expression);
                GlslAstHelpers.replaceNode(expr, parsed);
            }
        });
    }
}
