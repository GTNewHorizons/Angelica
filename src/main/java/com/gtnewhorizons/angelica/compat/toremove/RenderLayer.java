package com.gtnewhorizons.angelica.compat.toremove;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import lombok.Getter;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public abstract class RenderLayer extends RenderPhase { // Aka: RenderType (Iris)
    private static final RenderLayer SOLID = of("solid", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 2097152, true, false, RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).build(true));
    private static final RenderLayer CUTOUT_MIPPED = of("cutout_mipped", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 131072, true, false, RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).alpha(HALF_ALPHA).build(true));
    private static final RenderLayer CUTOUT = of("cutout", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 131072, true, false, RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(BLOCK_ATLAS_TEXTURE).alpha(HALF_ALPHA).build(true));
    private static final RenderLayer TRANSLUCENT = of("translucent", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 262144, true, true, createTranslucentPhaseData());
    private static final RenderLayer TRANSLUCENT_MOVING_BLOCK = of("translucent_moving_block", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 262144, false, true, getItemPhaseData());
    private static final RenderLayer TRANSLUCENT_NO_CRUMBLING = of("translucent_no_crumbling", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 262144, false, true, createTranslucentPhaseData());
    private static final RenderLayer LEASH = of("leash", DefaultVertexFormat.POSITION_COLOR_LIGHT, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).build(false));
    private static final RenderLayer WATER_MASK = of("water_mask", DefaultVertexFormat.POSITION, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).writeMaskState(DEPTH_MASK).build(false));
//    private static final RenderLayer ARMOR_GLINT = of("armor_glint", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(GLINT_TEXTURING).layering(VIEW_OFFSET_Z_LAYERING).build(false));
//    private static final RenderLayer ARMOR_ENTITY_GLINT = of("armor_entity_glint", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(ENTITY_GLINT_TEXTURING).layering(VIEW_OFFSET_Z_LAYERING).build(false));
//    private static final RenderLayer GLINT_TRANSLUCENT = of("glint_translucent", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(GLINT_TEXTURING).target(ITEM_TARGET).build(false));
//    private static final RenderLayer GLINT = of("glint", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(GLINT_TEXTURING).build(false));
//    private static final RenderLayer DIRECT_GLINT = of("glint_direct", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(GLINT_TEXTURING).build(false));
//    private static final RenderLayer ENTITY_GLINT = of("entity_glint", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).target(ITEM_TARGET).texturing(ENTITY_GLINT_TEXTURING).build(false));
//    private static final RenderLayer DIRECT_ENTITY_GLINT = of("entity_glint_direct", DefaultVertexFormat.POSITION_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(ItemRenderer.ENCHANTED_ITEM_GLINT, true, false)).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).depthTest(EQUAL_DEPTH_TEST).transparency(GLINT_TRANSPARENCY).texturing(ENTITY_GLINT_TEXTURING).build(false));
    private static final RenderLayer LIGHTNING = of("lightning", DefaultVertexFormat.POSITION_COLOR, 7, 256, false, true, RenderLayer.MultiPhaseParameters.builder().writeMaskState(ALL_MASK).transparency(LIGHTNING_TRANSPARENCY).target(WEATHER_TARGET).shadeModel(SMOOTH_SHADE_MODEL).build(false));
    private static final RenderLayer TRIPWIRE = of("tripwire", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 262144, true, true, getTripwirePhaseData());
    private static final RenderLayer LINES = of("lines", DefaultVertexFormat.POSITION_COLOR, 1, 256, RenderLayer.MultiPhaseParameters.builder().lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty())).layering(VIEW_OFFSET_Z_LAYERING).transparency(TRANSLUCENT_TRANSPARENCY).target(ITEM_TARGET).writeMaskState(ALL_MASK).build(false));

    private static List<RenderLayer> BLOCK_LAYERS = ImmutableList.of(RenderLayer.solid(), RenderLayer.cutoutMipped(), RenderLayer.cutout(), RenderLayer.translucent(), RenderLayer.tripwire());

    @Getter
    private final VertexFormat vertexFormat;
    @Getter
    private final int drawMode;
    @Getter
    private final int expectedBufferSize;
    private final boolean hasCrumbling;
    private final boolean translucent;
    private final Optional<RenderLayer> optionalThis;

    private static MultiPhaseParameters getTripwirePhaseData() {
        return MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).target(WEATHER_TARGET).build(true);
    }

    public RenderLayer(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, startAction, endAction);
        this.vertexFormat = vertexFormat;
        this.drawMode = drawMode;
        this.expectedBufferSize = expectedBufferSize;
        this.hasCrumbling = hasCrumbling;
        this.translucent = translucent;
        this.optionalThis = Optional.of(this);
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

    public static RenderLayer cutoutMipped() {
        return CUTOUT_MIPPED;
    }
    private static MultiPhaseParameters createTranslucentPhaseData() {
        return RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).target(TRANSLUCENT_TARGET).build(true);
    }

    public static RenderLayer translucent() {
        return TRANSLUCENT;
    }

    private static MultiPhaseParameters getItemPhaseData() {
        return RenderLayer.MultiPhaseParameters.builder().shadeModel(SMOOTH_SHADE_MODEL).lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).target(ITEM_TARGET).build(true);
    }

    public static RenderLayer getArmorCutoutNoCull(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(NO_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).layering(VIEW_OFFSET_Z_LAYERING).build(true);
        return of("armor_cutout_no_cull", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, false, rendertype$state);
    }

    public static RenderLayer getEntitySolid(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(NO_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(true);
        return of("entity_solid", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, false, rendertype$state);
    }

    public static RenderLayer getEntityCutout(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(NO_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(true);
        return of("entity_cutout", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, false, rendertype$state);
    }

    public static RenderLayer getEntityCutoutNoCull(ResourceLocation texture, boolean affectsOutline) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(NO_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(affectsOutline);
        return of("entity_cutout_no_cull", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, false, rendertype$state);
    }

    public static RenderLayer getEntityCutoutNoCull(ResourceLocation texture) {
        return getEntityCutoutNoCull(texture, true);
    }

    public static RenderLayer getEntityCutoutNoCullZOffset(ResourceLocation texture, boolean affectsOutline) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(NO_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).layering(VIEW_OFFSET_Z_LAYERING).build(affectsOutline);
        return of("entity_cutout_no_cull_z_offset", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, false, rendertype$state);
    }

    public static RenderLayer getEntityCutoutNoCullZOffset(ResourceLocation texture) {
        return getEntityCutoutNoCullZOffset(texture, true);
    }

    public static RenderLayer getItemEntityTranslucentCull(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).target(ITEM_TARGET).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).writeMaskState(RenderPhase.ALL_MASK).build(true);
        return of("item_entity_translucent_cull", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, true, rendertype$state);
    }

    public static RenderLayer getEntityTranslucentCull(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(true);
        return of("entity_translucent_cull", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, true, rendertype$state);
    }

    public static RenderLayer getEntityTranslucent(ResourceLocation texture, boolean affectsOutline) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(affectsOutline);
        return of("entity_translucent", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, true, true, rendertype$state);
    }

    public static RenderLayer getEntityTranslucent(ResourceLocation texture) {
        return getEntityTranslucent(texture, true);
    }

    public static RenderLayer getEntitySmoothCutout(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).alpha(HALF_ALPHA).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).shadeModel(SMOOTH_SHADE_MODEL).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).build(true);
        return of("entity_smooth_cutout", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, rendertype$state);
    }

    public static RenderLayer getBeaconBeam(ResourceLocation texture, boolean translucent) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY).writeMaskState(translucent ? COLOR_MASK : ALL_MASK).fog(NO_FOG).build(false);
        return of("beacon_beam", DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 7, 256, false, true, rendertype$state);
    }

    public static RenderLayer getEntityDecal(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).depthTest(EQUAL_DEPTH_TEST).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(false);
        return of("entity_decal", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, rendertype$state);
    }

    public static RenderLayer getEntityNoOutline(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).writeMaskState(COLOR_MASK).build(false);
        return of("entity_no_outline", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, false, true, rendertype$state);
    }

    public static RenderLayer getEntityShadow(ResourceLocation texture) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).transparency(TRANSLUCENT_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(ENABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).writeMaskState(COLOR_MASK).depthTest(LEQUAL_DEPTH_TEST).layering(VIEW_OFFSET_Z_LAYERING).build(false);
        return of("entity_shadow", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, false, false, rendertype$state);
    }

    public static RenderLayer getEntityAlpha(ResourceLocation texture, float alpha) {
        MultiPhaseParameters rendertype$state = RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).alpha(new RenderPhase.Alpha(alpha)).cull(DISABLE_CULLING).build(true);
        return of("entity_alpha", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, rendertype$state);
    }

    public static RenderLayer getEyes(ResourceLocation texture) {
        RenderPhase.Texture renderstate$texturestate = new RenderPhase.Texture(texture, false, false);
        return of("eyes", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, false, true, RenderLayer.MultiPhaseParameters.builder().texture(renderstate$texturestate).transparency(ADDITIVE_TRANSPARENCY).writeMaskState(COLOR_MASK).fog(BLACK_FOG).build(false));
    }

    public static RenderLayer getEnergySwirl(ResourceLocation texture, float x, float y) {
        return of("energy_swirl", DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, 7, 256, false, true, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).texturing(new RenderPhase.OffsetTexturing(x, y)).fog(BLACK_FOG).transparency(ADDITIVE_TRANSPARENCY).diffuseLighting(ENABLE_DIFFUSE_LIGHTING).alpha(ONE_TENTH_ALPHA).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).build(false));
    }

    public static RenderLayer getOutline(ResourceLocation texture) {
        return getOutline(texture, DISABLE_CULLING);
    }

    public static RenderLayer getOutline(ResourceLocation texture, RenderPhase.Cull cull) {
        return of("outline", DefaultVertexFormat.POSITION_COLOR_TEXTURE, 7, 256, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, false, false)).cull(cull).depthTest(ALWAYS_DEPTH_TEST).alpha(ONE_TENTH_ALPHA).texturing(OUTLINE_TEXTURING).fog(NO_FOG).target(OUTLINE_TARGET).build(RenderLayer.OutlineMode.IS_OUTLINE));
    }


    public static RenderLayer tripwire() {
        return TRIPWIRE;
    }

    public static List<RenderLayer> getBlockLayers() {
        return BLOCK_LAYERS;
    }

    public int mode() {
        return this.drawMode;
    }

    public VertexFormat format() {
        return vertexFormat;
    }

    public boolean shouldSortOnUpload() {
        return false;
    }

    public int bufferSize() {
        return 0;
    }

    public void end(BufferBuilder buffer, int i, int i1, int i2) {}

    public boolean hasCrumbling() {
        return hasCrumbling;
    }

    public Optional<RenderLayer> outline() {
        return Optional.empty();
    }

    public boolean isOutline() {
        return false;
    }

    public Optional<RenderLayer> asOptional() {
        return optionalThis;
    }

    public void draw(BufferBuilder lv, int cameraX, int cameraY, int cameraZ) {
    }


    static final class MultiPhase extends RenderLayer {
        private static final ObjectOpenCustomHashSet<MultiPhase> CACHE;
        private final MultiPhaseParameters phases;
        private final int hash;
        private final Optional<RenderLayer> affectedOutline;
        private final boolean outline;

        private MultiPhase(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, MultiPhaseParameters phases) {
            super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, () -> {
                phases.phases.forEach(RenderPhase::startDrawing);
            }, () -> {
                phases.phases.forEach(RenderPhase::endDrawing);
            });
            this.phases = phases;
            this.affectedOutline = phases.outlineMode == RenderLayer.OutlineMode.AFFECTS_OUTLINE ? phases.texture.getId().map((arg2) -> {
                return getOutline(arg2, phases.cull);
            }) : Optional.empty();
            this.outline = phases.outlineMode == RenderLayer.OutlineMode.IS_OUTLINE;
            this.hash = Objects.hash(new Object[]{super.hashCode(), phases});
        }

        public static MultiPhase of(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent,
            MultiPhaseParameters phases) {
            return (MultiPhase)CACHE.addOrGet(new MultiPhase(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, phases));
        }

        public Optional<RenderLayer> getAffectedOutline() {
            return this.affectedOutline;
        }

        public boolean isOutline() {
            return this.outline;
        }

        public boolean equals(@Nullable Object object) {
            return this == object;
        }

        public int hashCode() {
            return this.hash;
        }

        public String toString() {
            return "RenderType[" + this.phases + ']';
        }

        static {
            CACHE = new ObjectOpenCustomHashSet(RenderLayer.MultiPhase.HashStrategy.INSTANCE);
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
        private final RenderPhase.Overlay overlay;
        private final RenderPhase.Fog fog;
        private final RenderPhase.Layering layering;
        private final RenderPhase.Target target;
        private final RenderPhase.Texturing texturing;
        private final RenderPhase.WriteMaskState writeMaskState;
        private final RenderPhase.LineWidth lineWidth;
        private final OutlineMode outlineMode;
        private final ImmutableList<RenderPhase> phases;

        private MultiPhaseParameters(RenderPhase.Texture texture, RenderPhase.Transparency transparency, RenderPhase.DiffuseLighting diffuseLighting, RenderPhase.ShadeModel shadeModel, RenderPhase.Alpha alpha, RenderPhase.DepthTest depthTest, RenderPhase.Cull cull, RenderPhase.Lightmap lightmap, RenderPhase.Overlay overlay, RenderPhase.Fog fog, RenderPhase.Layering layering, RenderPhase.Target target, RenderPhase.Texturing texturing, RenderPhase.WriteMaskState writeMaskState, RenderPhase.LineWidth lineWidth, OutlineMode outlineMode) {
            this.texture = texture;
            this.transparency = transparency;
            this.diffuseLighting = diffuseLighting;
            this.shadeModel = shadeModel;
            this.alpha = alpha;
            this.depthTest = depthTest;
            this.cull = cull;
            this.lightmap = lightmap;
            this.overlay = overlay;
            this.fog = fog;
            this.layering = layering;
            this.target = target;
            this.texturing = texturing;
            this.writeMaskState = writeMaskState;
            this.lineWidth = lineWidth;
            this.outlineMode = outlineMode;
            this.phases = ImmutableList.of(this.texture, this.transparency, this.diffuseLighting, this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.overlay, this.fog, this.layering, this.target, new RenderPhase[]{this.texturing, this.writeMaskState, this.lineWidth});
        }

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

        public int hashCode() {
            return Objects.hash(new Object[]{this.phases, this.outlineMode});
        }

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
            private RenderPhase.Overlay overlay;
            private RenderPhase.Fog fog;
            private RenderPhase.Layering layering;
            private RenderPhase.Target target;
            private RenderPhase.Texturing texturing;
            private RenderPhase.WriteMaskState writeMaskState;
            private RenderPhase.LineWidth lineWidth;

            private Builder() {
                this.texture = RenderPhase.NO_TEXTURE ;
                this.transparency = RenderPhase.NO_TRANSPARENCY;
                this.diffuseLighting = RenderPhase.DISABLE_DIFFUSE_LIGHTING;
                this.shadeModel = RenderPhase.SHADE_MODEL;
                this.alpha = RenderPhase.ZERO_ALPHA;
                this.depthTest = RenderPhase.LEQUAL_DEPTH_TEST;
                this.cull = RenderPhase.ENABLE_CULLING;
                this.lightmap = RenderPhase.DISABLE_LIGHTMAP;
                this.overlay = RenderPhase.DISABLE_OVERLAY_COLOR;
                this.fog = RenderPhase.FOG;
                this.layering = RenderPhase.NO_LAYERING;
                this.target = RenderPhase.MAIN_TARGET;
                this.texturing = RenderPhase.DEFAULT_TEXTURING;
                this.writeMaskState = RenderPhase.ALL_MASK;
                this.lineWidth = RenderPhase.FULL_LINE_WIDTH;
            }

            public Builder texture(RenderPhase.Texture texture) {
                this.texture = texture;
                return this;
            }

            public Builder transparency(RenderPhase.Transparency transparency) {
                this.transparency = transparency;
                return this;
            }

            public Builder diffuseLighting(RenderPhase.DiffuseLighting diffuseLighting) {
                this.diffuseLighting = diffuseLighting;
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

            public Builder overlay(RenderPhase.Overlay overlay) {
                this.overlay = overlay;
                return this;
            }

            public Builder fog(RenderPhase.Fog fog) {
                this.fog = fog;
                return this;
            }

            public Builder layering(RenderPhase.Layering layering) {
                this.layering = layering;
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

            public Builder lineWidth(RenderPhase.LineWidth lineWidth) {
                this.lineWidth = lineWidth;
                return this;
            }

            public MultiPhaseParameters build(boolean affectsOutline) {
                return this.build(affectsOutline ? RenderLayer.OutlineMode.AFFECTS_OUTLINE : RenderLayer.OutlineMode.NONE);
            }

            public MultiPhaseParameters build(OutlineMode outlineMode) {
                return new MultiPhaseParameters(this.texture, this.transparency, this.diffuseLighting, this.shadeModel, this.alpha, this.depthTest, this.cull, this.lightmap, this.overlay, this.fog, this.layering, this.target, this.texturing, this.writeMaskState, this.lineWidth, outlineMode);
            }
        }
    }

    static enum OutlineMode {
        NONE("none"),
        IS_OUTLINE("is_outline"),
        AFFECTS_OUTLINE("affects_outline");

        private final String name;

        private OutlineMode(String name) {
            this.name = name;
        }

        public String toString() {
            return this.name;
        }
    }


}
