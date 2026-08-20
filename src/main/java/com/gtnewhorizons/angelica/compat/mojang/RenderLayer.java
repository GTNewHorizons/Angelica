package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.ImmutableList;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import lombok.Getter;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public abstract class RenderLayer extends RenderPhase { // Aka: RenderType (Iris)
    private static final RenderLayer SOLID = of("solid", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 2097152, true, false, RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).build(true));
    private static final RenderLayer CUTOUT = of("cutout", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 131072, true, false, RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(BLOCK_ATLAS_TEXTURE).alpha(HALF_ALPHA).build(true));
    private static final RenderLayer TRANSLUCENT = of("translucent", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 262144, true, true, createTranslucentPhaseData());

    @Getter
    private final VertexFormat vertexFormat;
    @Getter
    private final int drawMode;
    @Getter
    private final int expectedBufferSize;

    public RenderLayer(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, Runnable startAction, Runnable endAction) {
        super(name, startAction, endAction);
        this.vertexFormat = vertexFormat;
        this.drawMode = drawMode;
        this.expectedBufferSize = expectedBufferSize;
    }


    public static MultiPhase of(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, MultiPhaseParameters phaseData) {
        return of(name, vertexFormat, drawMode, expectedBufferSize, false, false, phaseData);
    }

    public static MultiPhase of(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, MultiPhaseParameters phases) {
        return RenderLayer.MultiPhase.of(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, phases);
    }


    public static RenderLayer solid() {
        return SOLID;
    }

    public static RenderLayer cutout() {
        return CUTOUT;
    }

    private static MultiPhaseParameters createTranslucentPhaseData() {
        return RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).target(TRANSLUCENT_TARGET).build(true);
    }

    public static RenderLayer translucent() {
        return TRANSLUCENT;
    }

    private static MultiPhaseParameters.Builder tesrMaterialPhases(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits) {
        final MultiPhaseParameters.Builder b = MultiPhaseParameters.builder();
        b.texture(texture != null ? new RenderPhase.Texture(texture, false, false) : NO_TEXTURE);
        if (material.isNoCull()) {
            b.cull(DISABLE_CULLING);
        }
        if (material.isUnlit()) {
            b.texturing(NO_FFP_LIGHTING);
        }
        if (material.isNoDepthWrite()) {
            b.writeMaskState(COLOR_MASK);
        }
        if (material.isDepthOnly()) {
            b.writeMaskState(DEPTH_MASK);
        }
        b.alpha(material.cutoutAlpha() > 0 ? new RenderPhase.Alpha(material.cutoutAlpha()) : ZERO_ALPHA);
        b.depthTest(material.isDepthEqual() ? EQUAL_DEPTH_TEST : LEQUAL_DEPTH_TEST);
        switch (material.transparency()) {
            case TRANSLUCENT -> b.transparency(TRANSLUCENT_TRANSPARENCY);
            case ADDITIVE -> b.transparency(ADDITIVE_TRANSPARENCY);
            case ADDITIVE_ALPHA -> b.transparency(LIGHTNING_TRANSPARENCY);
            case GLINT -> b.transparency(GLINT_TRANSPARENCY);
            case OPAQUE -> b.transparency(NO_TRANSPARENCY);
        }
        boolean shaderPhaseTaken = true;
        switch (material.special()) {
            case GLINT -> b.shader(SPECIAL_GLINT);
            case BEACON_BEAM -> b.shader(SPECIAL_BEACON_BEAM);
            case NONE -> {
                final TesrShader shader = material.shader();
                if (shader != null) {
                    b.shader(new RenderPhase.Shader("angelica_tesr_shader_" + shader.name(), shader.bind(), shader.release()));
                } else {
                    shaderPhaseTaken = false;
                }
            }
        }
        if (!shaderPhaseTaken && pass != PassOverride.NONE) {
            b.shader(new RenderPhase.Shader("angelica_tesr_pass" + pass.nameSuffix(), pass::apply, pass::clear));
        }
        b.layering(polygonOffset(offsetFactor, offsetUnits));
        return b;
    }

    private static final RenderPhase.Shader SPECIAL_GLINT = new RenderPhase.Shader("angelica_special_glint",
        () -> GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT),
        GbufferPrograms::teardownSpecialRenderCondition);
    private static final RenderPhase.Shader SPECIAL_BEACON_BEAM = new RenderPhase.Shader("angelica_special_beacon_beam",
        () -> GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM),
        GbufferPrograms::teardownSpecialRenderCondition);

    public static RenderLayer tesr(ResourceLocation texture, TesrMaterial material) {
        return tesr(texture, material, PassOverride.NONE, 0.0f, 0.0f);
    }

    public static RenderLayer tesr(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits) {
        final MultiPhaseParameters.Builder b = tesrMaterialPhases(texture, material, pass, offsetFactor, offsetUnits).shadeModel(SMOOTH_SHADE_MODEL);
        final boolean sortsTranslucent = material.transparency() != TesrMaterial.Transparency.OPAQUE;
        return of("angelica_tesr_" + material.transparency().name().toLowerCase(Locale.ROOT) + pass.nameSuffix()
                + (offsetFactor == 0.0f && offsetUnits == 0.0f ? "" : "_offset" + offsetFactor + "_" + offsetUnits),
            BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL, GL11.GL_QUADS, 65536, true, sortsTranslucent, b.build(false));
    }

    public static RenderLayer tesrNoPass(ResourceLocation texture, TesrMaterial material) {
        return of("angelica_tesr_nopass_" + material.transparency().name().toLowerCase(Locale.ROOT),
            BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL, GL11.GL_QUADS, 65536, true, false,
            tesrMaterialPhases(texture, material, PassOverride.NONE, 0.0f, 0.0f).build(false));
    }

    public static RenderLayer getOutline(ResourceLocation texture, RenderPhase.Cull cull) {
        return of("outline", DefaultVertexFormat.POSITION_COLOR_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).cull(cull).depthTest(ALWAYS_DEPTH_TEST).alpha(ONE_TENTH_ALPHA).fog(NO_FOG).target(OUTLINE_TARGET).build(RenderLayer.OutlineMode.IS_OUTLINE));
    }

    public int mode() {
        return this.drawMode;
    }

    public ResourceLocation getTextureId() {
        return null;
    }

    static final class MultiPhase extends RenderLayer implements BlendingStateHolder {
        private static final ObjectOpenCustomHashSet<MultiPhase> CACHE;
        private final MultiPhaseParameters phases;
        private final int hash;
        private final Optional<RenderLayer> affectedOutline;

        @Override
        public TransparencyType getTransparencyType() {
            return phases.transparency == null ? TransparencyType.OPAQUE : phases.transparency.getTransparencyType();
        }

        private MultiPhase(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, MultiPhaseParameters phases) {
            super(name, vertexFormat, drawMode, expectedBufferSize, () -> {
                final ImmutableList<RenderPhase> p = phases.phases;
                for (int i = 0, n = p.size(); i < n; i++) {
                    p.get(i).startDrawing();
                }
            }, () -> {
                final ImmutableList<RenderPhase> p = phases.phases;
                for (int i = 0, n = p.size(); i < n; i++) {
                    p.get(i).endDrawing();
                }
            });
            this.phases = phases;
            this.affectedOutline = phases.outlineMode == RenderLayer.OutlineMode.AFFECTS_OUTLINE && phases.texture != null ? phases.texture.getId().map((arg2) -> {
                return getOutline(arg2, phases.cull);
            }) : Optional.empty();
            this.hash = Objects.hash(new Object[]{super.hashCode(), phases});
        }

        public static MultiPhase of(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent,
            MultiPhaseParameters phases) {
            return (MultiPhase)CACHE.addOrGet(new MultiPhase(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, phases));
        }

        public Optional<RenderLayer> getAffectedOutline() {
            return this.affectedOutline;
        }

        @Override
        public ResourceLocation getTextureId() {
            return phases.texture == null ? null : phases.texture.getId().orElse(null);
        }

        @Override
        public boolean equals(@Nullable Object object) {
            return this == object;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public String toString() {
            return "RenderType[" + this.phases + ']';
        }

        static {
            CACHE = new ObjectOpenCustomHashSet<>(RenderLayer.MultiPhase.HashStrategy.INSTANCE);
        }

        static enum HashStrategy implements Hash.Strategy<MultiPhase> {
            INSTANCE;

            private HashStrategy() {
            }

            public int hashCode(@Nullable MultiPhase arg) {
                return arg == null ? 0 : arg.hash;
            }

            public boolean equals(@Nullable MultiPhase arg, @Nullable MultiPhase arg2) {
                if (arg == arg2) {
                    return true;
                } else {
                    return arg != null && arg2 != null && arg.name.equals(arg2.name)
                        && arg.getVertexFormat() == arg2.getVertexFormat() && arg.getDrawMode() == arg2.getDrawMode()
                        && Objects.equals(arg.phases, arg2.phases);
                }
            }
        }
    }

    public static final class MultiPhaseParameters {
        private final RenderPhase.Texture texture;
        private final RenderPhase.Transparency transparency;
        private final RenderPhase.DiffuseLighting diffuseLighting;
        private final RenderPhase.ShadeModel shadeModel;
        private final RenderPhase.Alpha alpha;
        private final RenderPhase.DepthTest depthTest;
        private final RenderPhase.Cull cull;
        private final RenderPhase.Lightmap lightmap;
        private final RenderPhase.Fog fog;
        private final RenderPhase.Layering layering;
        private final RenderPhase.Target target;
        private final RenderPhase.Texturing texturing;
        private final RenderPhase.WriteMaskState writeMaskState;
        private final RenderPhase.Shader shader;
        private final OutlineMode outlineMode;
        private final ImmutableList<RenderPhase> phases;

        private MultiPhaseParameters(RenderPhase.Texture texture, RenderPhase.Transparency transparency, RenderPhase.DiffuseLighting diffuseLighting, RenderPhase.ShadeModel shadeModel, RenderPhase.Alpha alpha, RenderPhase.DepthTest depthTest, RenderPhase.Cull cull, RenderPhase.Lightmap lightmap, RenderPhase.Fog fog, RenderPhase.Layering layering, RenderPhase.Target target, RenderPhase.Texturing texturing, RenderPhase.WriteMaskState writeMaskState, RenderPhase.Shader shader, OutlineMode outlineMode) {
            this.texture = texture;
            this.transparency = transparency;
            this.diffuseLighting = diffuseLighting;
            this.shadeModel = shadeModel;
            this.alpha = alpha;
            this.depthTest = depthTest;
            this.cull = cull;
            this.lightmap = lightmap;
            this.fog = fog;
            this.layering = layering;
            this.target = target;
            this.texturing = texturing;
            this.writeMaskState = writeMaskState;
            this.shader = shader;
            this.outlineMode = outlineMode;
            final ImmutableList.Builder<RenderPhase> declared = ImmutableList.builder();
            for (RenderPhase phase : new RenderPhase[] { this.texture, this.transparency, this.diffuseLighting,
                this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.fog, this.layering,
                this.target, this.texturing, this.writeMaskState, this.shader }) {
                if (phase != null) declared.add(phase);
            }
            this.phases = declared.build();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                MultiPhaseParameters rendertype$state = (MultiPhaseParameters)object;
                return this.outlineMode == rendertype$state.outlineMode && this.phases.equals(rendertype$state.phases);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(new Object[]{this.phases, this.outlineMode});
        }

        @Override
        public String toString() {
            return "CompositeState[" + this.phases + ", outlineProperty=" + this.outlineMode + ']';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private RenderPhase.Texture texture;
            private RenderPhase.Transparency transparency;
            private RenderPhase.DiffuseLighting diffuseLighting;
            private RenderPhase.ShadeModel shadeModel;
            private RenderPhase.Alpha alpha;
            private RenderPhase.DepthTest depthTest;
            private RenderPhase.Cull cull;
            private RenderPhase.Lightmap lightmap;
            private RenderPhase.Fog fog;
            private RenderPhase.Layering layering;
            private RenderPhase.Target target;
            private RenderPhase.Texturing texturing;
            private RenderPhase.WriteMaskState writeMaskState;
            private RenderPhase.Shader shader;

            private Builder() {}

            public Builder texture(RenderPhase.Texture texture) {
                this.texture = texture;
                return this;
            }

            public Builder transparency(RenderPhase.Transparency transparency) {
                this.transparency = transparency;
                return this;
            }

            public void layering(Layering layering) {
                this.layering = layering;
            }

            public Builder shadeModel(RenderPhase.ShadeModel shadeModel) {
                this.shadeModel = shadeModel;
                return this;
            }

            public Builder alpha(RenderPhase.Alpha alpha) {
                this.alpha = alpha;
                return this;
            }

            public Builder depthTest(RenderPhase.DepthTest depthTest) {
                this.depthTest = depthTest;
                return this;
            }

            public Builder cull(RenderPhase.Cull cull) {
                this.cull = cull;
                return this;
            }

            public Builder lightmap(RenderPhase.Lightmap lightmap) {
                this.lightmap = lightmap;
                return this;
            }

            public Builder fog(RenderPhase.Fog fog) {
                this.fog = fog;
                return this;
            }

            public Builder target(RenderPhase.Target target) {
                this.target = target;
                return this;
            }

            public Builder texturing(RenderPhase.Texturing texturing) {
                this.texturing = texturing;
                return this;
            }

            public Builder writeMaskState(RenderPhase.WriteMaskState writeMaskState) {
                this.writeMaskState = writeMaskState;
                return this;
            }

            public Builder shader(RenderPhase.Shader shader) {
                this.shader = shader;
                return this;
            }

            public MultiPhaseParameters build(boolean affectsOutline) {
                return this.build(affectsOutline ? RenderLayer.OutlineMode.AFFECTS_OUTLINE : RenderLayer.OutlineMode.NONE);
            }

            public MultiPhaseParameters build(OutlineMode outlineMode) {
                return new MultiPhaseParameters(this.texture, this.transparency, this.diffuseLighting, this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.fog, this.layering, this.target, this.texturing, this.writeMaskState, this.shader, outlineMode);
            }
        }
    }

    enum OutlineMode {
        NONE("none"),
        IS_OUTLINE("is_outline"),
        AFFECTS_OUTLINE("affects_outline");

        private final String name;

        OutlineMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }


}
