package com.gtnewhorizons.angelica.compat.toremove;

import com.google.common.collect.ImmutableList;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import lombok.Getter;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
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

    public static RenderLayer getOutline(ResourceLocation texture, RenderPhase.Cull cull) {
        return of("outline", DefaultVertexFormat.POSITION_COLOR_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).cull(cull).depthTest(ALWAYS_DEPTH_TEST).alpha(ONE_TENTH_ALPHA).fog(NO_FOG).target(OUTLINE_TARGET).build(RenderLayer.OutlineMode.IS_OUTLINE));
    }

    public int mode() {
        return this.drawMode;
    }

    static final class MultiPhase extends RenderLayer {
        private static final ObjectOpenCustomHashSet<MultiPhase> CACHE;
        private final MultiPhaseParameters phases;
        private final int hash;
        private final Optional<RenderLayer> affectedOutline;

        private MultiPhase(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, MultiPhaseParameters phases) {
            super(name, vertexFormat, drawMode, expectedBufferSize, () -> {
                phases.phases.forEach(RenderPhase::startDrawing);
            }, () -> {
                phases.phases.forEach(RenderPhase::endDrawing);
            });
            this.phases = phases;
            this.affectedOutline = phases.outlineMode == RenderLayer.OutlineMode.AFFECTS_OUTLINE ? phases.texture.getId().map((arg2) -> {
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
                    return arg != null && arg2 != null ? Objects.equals(arg.phases, arg2.phases) : false;
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
        private final OutlineMode outlineMode;
        private final ImmutableList<RenderPhase> phases;

        private MultiPhaseParameters(RenderPhase.Texture texture, RenderPhase.Transparency transparency, RenderPhase.DiffuseLighting diffuseLighting, RenderPhase.ShadeModel shadeModel, RenderPhase.Alpha alpha, RenderPhase.DepthTest depthTest, RenderPhase.Cull cull, RenderPhase.Lightmap lightmap, RenderPhase.Fog fog, RenderPhase.Layering layering, RenderPhase.Target target, RenderPhase.Texturing texturing, RenderPhase.WriteMaskState writeMaskState, OutlineMode outlineMode) {
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
            this.outlineMode = outlineMode;
            this.phases = ImmutableList.of(this.texture, this.transparency, this.diffuseLighting, this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.fog, this.layering, this.target, this.texturing, this.writeMaskState);
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

            private Builder() {
                this.texture = RenderPhase.NO_TEXTURE ;
                this.transparency = RenderPhase.NO_TRANSPARENCY;
                this.diffuseLighting = RenderPhase.DISABLE_DIFFUSE_LIGHTING;
                this.shadeModel = RenderPhase.SHADE_MODEL;
                this.alpha = RenderPhase.ZERO_ALPHA;
                this.depthTest = RenderPhase.LEQUAL_DEPTH_TEST;
                this.cull = RenderPhase.ENABLE_CULLING;
                this.lightmap = RenderPhase.DISABLE_LIGHTMAP;
                this.fog = RenderPhase.FOG;
                this.layering = RenderPhase.NO_LAYERING;
                this.target = RenderPhase.MAIN_TARGET;
                this.texturing = RenderPhase.DEFAULT_TEXTURING;
                this.writeMaskState = RenderPhase.ALL_MASK;
            }

            public Builder texture(RenderPhase.Texture texture) {
                this.texture = texture;
                return this;
            }

            public Builder transparency(RenderPhase.Transparency transparency) {
                this.transparency = transparency;
                return this;
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

            public MultiPhaseParameters build(boolean affectsOutline) {
                return this.build(affectsOutline ? RenderLayer.OutlineMode.AFFECTS_OUTLINE : RenderLayer.OutlineMode.NONE);
            }

            public MultiPhaseParameters build(OutlineMode outlineMode) {
                return new MultiPhaseParameters(this.texture, this.transparency, this.diffuseLighting, this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.fog, this.layering, this.target, this.texturing, this.writeMaskState, outlineMode);
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
