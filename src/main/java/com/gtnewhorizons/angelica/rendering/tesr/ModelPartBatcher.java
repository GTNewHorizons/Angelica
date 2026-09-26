package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.api.tesr.ModelPartMeshBuilder;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import com.gtnewhorizons.angelica.mixins.interfaces.ModelBoxData;
import com.gtnewhorizons.angelica.profiling.BailClassCounts;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Objects;

public final class ModelPartBatcher {

    public static final ModelPartBatcher INSTANCE = new ModelPartBatcher();

    public enum Mode { ENTITIES, BLOCK_ENTITIES }

    public enum BailReason {
        INACTIVE("tesr.bail.inactive"),
        RECORDING("tesr.bail.recording"),
        MATERIAL("tesr.bail.material"),
        TEXTURE("tesr.bail.texture"),
        TEMPLATE("tesr.bail.template"),
        FOREIGN_PROGRAM("tesr.bail.foreignProgram"),
        MIXED_RENDERER("tesr.bail.mixedRenderer");

        public static final BailReason[] VALUES = values();
        public final String plotName;

        BailReason(String plotName) {
            this.plotName = plotName;
        }
    }

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private static final Tracy.ZoneId Z_ENTITY_LAYER_LOOP = Tracy.zoneId("entityLayerLoop", Tracy.COLOR_CLIENT);

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
    private final EntityShadowBatcher shadows = new EntityShadowBatcher();
    private final RetainedTesrGroups groups = new RetainedTesrGroups(bufferSource);
    private final RetainedTesrGroups shadowGroups = new RetainedTesrGroups(bufferSource);
    private RetainedTesrGroups activeGroups = groups;
    private boolean shadow;
    private final AngelicaTesrMeshCache.GtnhMeshBackend captureBackend = new AngelicaTesrMeshCache.GtnhMeshBackend();
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f texMatrixScratch = new Matrix4f();
    private int lastMvGeneration;
    private boolean mvCaptured;
    private int texGeneration;
    private boolean texCaptured;
    private boolean texIdentity;
    private boolean active;
    private Mode mode = Mode.BLOCK_ENTITIES;
    private boolean instancedThisCycle;
    private DeferredWorldRenderingPipeline shaderPipeline;
    private long lastSweepMs;

    long parts;
    long liveFallbacks;
    private final long[] bails = new long[BailReason.VALUES.length];
    private long lastParts, lastLiveFallbacks;

    public long statParts() { return parts; }
    public long statLiveFallbacks() { return liveFallbacks; }
    public long statInstancedDraws() { return groups.instancedDraws + shadowGroups.instancedDraws; }
    public long statInstancedInstances() { return groups.instancedInstances + shadowGroups.instancedInstances; }
    public long statCubeInstances() { return groups.cubeInstances + shadowGroups.cubeInstances; }
    public long statTexMatrixRuns() { return groups.texMatrixRuns + shadowGroups.texMatrixRuns; }
    public long statBail(BailReason reason) { return bails[reason.ordinal()]; }
    public long statShadowQuads() { return shadows.statQuads(); }
    public long statShadowDraws() { return shadows.statDraws(); }

    private void bail(BailReason reason) {
        bails[reason.ordinal()]++;
    }

    private static final class PartTemplate {
        TemplateBuffer template;
        CubeParams[] cubes;
        float scale;
        long lastUsedMs;
        boolean emptyVanillaPart;
    }

    private final Reference2ObjectOpenHashMap<ModelRenderer, PartTemplate> templates = new Reference2ObjectOpenHashMap<>();

    private static final class LayerKey {
        ResourceLocation texture;
        TesrMaterial material;
        PassOverride pass;
        float offsetFactor;
        float offsetUnits;
        int glintSlot;
        int cull;
        boolean lit;

        LayerKey set(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit) {
            this.texture = texture;
            this.material = material;
            this.pass = pass;
            this.offsetFactor = offsetFactor;
            this.offsetUnits = offsetUnits;
            this.glintSlot = glintSlot;
            this.cull = cull;
            this.lit = lit;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayerKey other)) return false;
            return Objects.equals(texture, other.texture) && Objects.equals(material, other.material)
                && Objects.equals(pass, other.pass)
                && Float.floatToIntBits(offsetFactor) == Float.floatToIntBits(other.offsetFactor)
                && Float.floatToIntBits(offsetUnits) == Float.floatToIntBits(other.offsetUnits)
                && glintSlot == other.glintSlot && cull == other.cull && lit == other.lit;
        }

        @Override
        public int hashCode() {
            int h = (Objects.hashCode(texture) * 31 + Objects.hashCode(material)) * 31 + Objects.hashCode(pass);
            h = h * 31 + Float.floatToIntBits(offsetFactor);
            h = h * 31 + Float.floatToIntBits(offsetUnits);
            h = h * 31 + glintSlot;
            h = h * 31 + cull;
            h = h * 31 + (lit ? 1 : 0);
            return h;
        }
    }

    private final Object2ObjectOpenHashMap<LayerKey, RenderLayer> layers = new Object2ObjectOpenHashMap<>();
    private final LayerKey scratchKey = new LayerKey();
    private ResourceLocation lastLayerTexture;
    private TesrMaterial lastLayerMaterial;
    private PassOverride lastLayerPass;
    private float lastLayerOffsetFactor;
    private float lastLayerOffsetUnits;
    private int lastLayerGlintSlot;
    private int lastLayerCull;
    private boolean lastLayerLit;
    private RenderLayer lastLayer;

    private static ResourceLocation lastBoundLocation;
    private static int lastBoundGlId = -1;
    private static int lastBoundGlintSlot = ShaderGlint.NO_TINT;

    private ModelPartBatcher() {}

    public static void onTextureSwap(int glId, int glintSlot) {
        lastBoundGlId = glId;
        lastBoundGlintSlot = glintSlot;
    }

    public static void onTextureBind(ResourceLocation location, int glId) {
        lastBoundLocation = location;
        lastBoundGlId = glId;
        lastBoundGlintSlot = ShaderGlint.NO_TINT;
    }

    public void begin(Mode mode) {
        begin(mode, false);
    }

    public void begin(Mode mode, boolean shadow) {
        this.mode = mode;
        this.shadow = shadow;
        instancedThisCycle = TesrBatchRenderer.instancedCapable();
        shaderPipeline = TesrBatchRenderer.deferredPipeline();
        if (!AngelicaConfig.enableEntityBatching || !(instancedThisCycle || shaderPipeline != null)) {
            active = false;
            shaderPipeline = null;
            return;
        }
        active = true;
        final long nowMs = System.currentTimeMillis();
        mvCaptured = false;
        texCaptured = false;
        activeGroups = shadow ? shadowGroups : groups;
        bufferSource.setIdKind(mode == Mode.ENTITIES ? AngelicaBufferSource.GroupIdKind.ENTITY : AngelicaBufferSource.GroupIdKind.BLOCK_ENTITY);
        activeGroups.beginPass(identity, 0, 0, 0, instancedThisCycle ? TesrBatchRenderer.INSTANCE.instancedRenderer : null, shaderPipeline);
        if (nowMs - lastSweepMs >= SWEEP_INTERVAL_MS) {
            lastSweepMs = nowMs;
            groups.sweep(nowMs);
            shadowGroups.sweep(nowMs);
            sweepTemplates(nowMs);
        }
    }

    public void flush() {
        if (!active) return;
        active = false;
        if (shadow) {
            drawBatches();
        } else {
            final boolean inEntityLoop = mode == Mode.ENTITIES && Iris.enabled && GbufferPrograms.isEntityLoopActive();
            final boolean wrapEntities = mode == Mode.ENTITIES && Iris.enabled && !inEntityLoop;
            if (wrapEntities) GbufferPrograms.beginEntities();
            else if (inEntityLoop) GbufferPrograms.onEntityRenderBoundary();
            try {
                shadows.flush(bufferSource);
                drawBatches();
            } finally {
                if (wrapEntities) GbufferPrograms.endEntities();
            }
        }
        if (instancedThisCycle) {
            TesrBatchRenderer.INSTANCE.instancedRenderer.endFrame();
        }
        if (!TesrBatchRenderer.INSTANCE.hasPendingGeometry()) {
            BatchingFontRenderer.flushDeferredText();
        }
    }

    private void drawBatches() {
        if (Tracy.ENABLED) Tracy.beginZone(Z_ENTITY_LAYER_LOOP);
        try {
            bufferSource.endBatchWithType(TransparencyType.OPAQUE, activeGroups);
            bufferSource.endBatch(activeGroups);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean entityPassActive() {
        return active && mode == Mode.ENTITIES && !shadow;
    }

    public boolean isShadowPass() {
        return active && shadow;
    }

    public static boolean recordShadow(World world, Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks, float shadowSize) {
        return INSTANCE.shadows.record(world, entity, x, y, z, shadowAlpha, partialTicks, shadowSize);
    }

    public static boolean partDraw(ModelRenderer part, float scale, ModelPartMeshBuilder builder) {
        final ModelPartBatcher r = INSTANCE;
        if (!r.active) {
            r.bail(BailReason.INACTIVE);
            return false;
        }
        if (!BatchEligibility.batchingAllowed()) {
            r.bail(BailReason.MIXED_RENDERER);
            return false;
        }
        if (DisplayListManager.isRecording()) {
            r.bail(BailReason.RECORDING);
            return false;
        }
        if (!r.queuePart(part, scale, builder)) {
            r.liveFallbacks++;
            return false;
        }
        BatchEligibility.onPartQueued();
        return true;
    }

    private boolean queuePart(ModelRenderer part, float scale, ModelPartMeshBuilder builder) {
        final PartTemplate entry = templateFor(part, scale, builder);
        if (entry.emptyVanillaPart) {
            return true;
        }
        if (shadow && isDepthEqualDecalState()) {
            return true;
        }
        return queueTemplate(entry.template, entry.cubes, scale, null);
    }

    public boolean queueTemplate(TemplateBuffer template, TesrMaterial material) {
        return queueTemplate(template, null, 1.0f, material);
    }

    private boolean queueTemplate(TemplateBuffer template, CubeParams[] cubes, float scale, TesrMaterial explicitMaterial) {
        if (shaderPipeline != null && GLStateManager.getActiveProgram() != shaderPipeline.getActivePassProgramId()) {
            bail(BailReason.FOREIGN_PROGRAM);
            return false;
        }
        final TesrMaterial material = explicitMaterial != null ? explicitMaterial : EntityMaterials.fromCurrentState(unitTexIdentity());
        if (material == null) {
            bail(BailReason.MATERIAL);
            if (Tracy.ENABLED) {
                BailClassCounts.MATERIAL.add(TesrAttribution.currentRenderable);
                logMaterialBail();
            }
            return false;
        }
        final ResourceLocation texture;
        if (material == EntityMaterials.OVERLAY) {
            texture = null;
        } else {
            if (GLStateManager.getBoundTextureForServerState() != lastBoundGlId) {
                bail(BailReason.TEXTURE);
                return false;
            }
            texture = lastBoundLocation;
            if (texture == null) {
                bail(BailReason.TEXTURE);
                return false;
            }
        }
        if (template == null) {
            bail(BailReason.TEMPLATE);
            if (Tracy.ENABLED) BailClassCounts.TEMPLATE.add(TesrAttribution.currentRenderable);
            return false;
        }

        final Matrix4f texMatrix = texture != null && !unitTexIdentity() ? texMatrixScratch : null;
        final PassOverride pass = shaderPipeline != null ? PassOverride.capture() : PassOverride.NONE;
        final boolean offset = GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        final PolygonState polygon = GLStateManager.getPolygonState();
        final float offsetFactor = offset ? polygon.getOffsetFactor() : 0.0f;
        final float offsetUnits = offset ? polygon.getOffsetUnits() : 0.0f;
        final int glintSlot = material.special() == TesrMaterial.SpecialRender.GLINT ? lastBoundGlintSlot : ShaderGlint.NO_TINT;
        final int cullCode = material.isNoCull() ? DrawState.DISABLED : DrawState.liveCull();
        final boolean lit = DrawState.liveLit(material);
        final RenderLayer layer = layerFor(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit);
        // Entities nested inside a TESR (mob spawner, OpenBlocks trophy) run in the block entity pass but carry an entity id
        final int entityId = groupId(shaderPipeline != null, mode == Mode.ENTITIES || pass.isEntityPhase(), CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
        final Color4 color = GLStateManager.getColor();
        final int colorABGR = AngelicaBufferSource.packAbgr(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        final int overlayABGR = shaderPipeline != null ? AngelicaBufferSource.packEntityColor(CapturedRenderingState.INSTANCE.getCurrentEntityColor()) : AngelicaBufferSource.packAbgr(GLStateManager.getOverlayR(), GLStateManager.getOverlayG(), GLStateManager.getOverlayB(), GLStateManager.getOverlayA());
        final long entityInfo = shaderPipeline != null ? InstancedAttribs.packEntityInfo(CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedItem()) : 0L;
        final int packedLight = GLSMConfig.packedLastBrightness();
        final int mvGen = GLStateManager.getMvGeneration();
        if (!mvCaptured || mvGen != lastMvGeneration) {
            modelView.set(GLStateManager.getModelViewMatrix());
            lastMvGeneration = mvGen;
            mvCaptured = true;
        }
        activeGroups.queue(template, cubes, scale, layer, material, modelView, packedLight, colorABGR, overlayABGR, entityInfo, entityId, texMatrix);
        parts++;
        return true;
    }

    private final ReferenceOpenHashSet<Class<?>> loggedMaterialBails = new ReferenceOpenHashSet<>();
    private final BlendState bailBlend = new BlendState();
    private final AlphaState bailAlpha = new AlphaState();

    private void logMaterialBail() {
        final Class<?> cls = TesrAttribution.currentRenderable;
        if (cls == null || !loggedMaterialBails.add(cls)) return;
        final boolean textured = GLStateManager.getTextures().getTextureUnitStates(0).isEnabled();
        final BlendState blendState = GLStateManager.getEffectiveBlendState(bailBlend);
        final AlphaState alphaState = GLStateManager.getEffectiveAlphaState(bailAlpha);
        AngelicaMod.LOGGER.info("TESR MATERIAL bail: cls={} shadow={} textured={} texAnimated={} blend={} src={} dst={} alphaTest={} alphaFunc={} alphaRef={} depthFunc={} depthMask={}", cls.getName(), shadow, textured, textured && !unitTexIdentity(), GLStateManager.isEffectiveBlendEnabled(), blendState.getSrcRgb(), blendState.getDstRgb(), GLStateManager.isEffectiveAlphaTestEnabled(), alphaState.getFunction(), alphaState.getReference(), GLStateManager.getDepthState().getFunc(),GLStateManager.isEffectiveDepthMaskEnabled());
    }

    static int groupId(boolean piped, boolean entityPhase, int entityId, int blockEntityId) {
        if (!piped) return 0;
        return entityPhase ? entityId : blockEntityId;
    }

    private boolean unitTexIdentity() {
        final int generation = GLStateManager.getTexMatrixGeneration();
        if (!texCaptured || generation != texGeneration) {
            final Matrix4f unitMatrix = GLStateManager.getTextures().getTextureUnitMatrix(0);
            texIdentity = MatrixHelper.isIdentity(unitMatrix);
            if (!texIdentity) texMatrixScratch.set(unitMatrix);
            texGeneration = generation;
            texCaptured = true;
        }
        return texIdentity;
    }

    private static boolean isDepthEqualDecalState() {
        return GLStateManager.getDepthState().getFunc() == GL11.GL_EQUAL && !GLStateManager.isEffectiveDepthMaskEnabled();
    }

    private PartTemplate templateFor(ModelRenderer part, float scale, ModelPartMeshBuilder builder) {
        PartTemplate entry = templates.get(part);
        if (entry == null || entry.scale != scale) {
            entry = capture(part, scale, entry, builder);
        }
        entry.lastUsedMs = lastSweepMs;
        return entry;
    }

    private PartTemplate capture(ModelRenderer part, float scale, PartTemplate reuse, ModelPartMeshBuilder builder) {
        final PartTemplate entry = reuse != null ? reuse : new PartTemplate();
        entry.scale = scale;
        entry.template = null;
        entry.cubes = null;
        entry.emptyVanillaPart = false;
        final List<ModelBox> boxes = part.cubeList;
        if (builder != null) {
            final Tessellator tess = captureBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
            try {
                builder.angelica$buildPart(tess, part, scale);
            } finally {
                entry.template = captureBackend.endCaptureToTemplate();
            }
        } else if (boxes != null && !boxes.isEmpty()) {
            final Tessellator tess = captureBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
            for (int i = 0, n = boxes.size(); i < n; i++) {
                boxes.get(i).render(tess, scale);
            }
            entry.template = captureBackend.endCaptureToTemplate();
            captureCubes(entry, boxes);
        } else {
            entry.emptyVanillaPart = part.getClass() == ModelRenderer.class;
        }
        templates.put(part, entry);
        return entry;
    }

    private static void captureCubes(PartTemplate entry, List<ModelBox> boxes) {
        final int n = boxes.size();
        CubeParams[] cubes = null;
        for (int i = 0; i < n; i++) {
            if (!(boxes.get(i) instanceof ModelBoxData data)) return;
            final CubeParams params = data.angelica$cubeParams();
            if (params == null) return;
            if (cubes == null) cubes = new CubeParams[n];
            cubes[i] = params;
        }
        entry.cubes = cubes;
    }

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cullCode, boolean lit) {
        if (texture == lastLayerTexture && material == lastLayerMaterial && pass.equals(lastLayerPass)
            && offsetFactor == lastLayerOffsetFactor && offsetUnits == lastLayerOffsetUnits && glintSlot == lastLayerGlintSlot
            && cullCode == lastLayerCull && lit == lastLayerLit) {
            return lastLayer;
        }
        RenderLayer layer = layers.get(scratchKey.set(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit));
        if (layer == null) {
            layer = RenderLayer.tesr(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit);
            layers.put(new LayerKey().set(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit), layer);
        }
        lastLayerTexture = texture;
        lastLayerMaterial = material;
        lastLayerPass = pass;
        lastLayerOffsetFactor = offsetFactor;
        lastLayerOffsetUnits = offsetUnits;
        lastLayerGlintSlot = glintSlot;
        lastLayerCull = cullCode;
        lastLayerLit = lit;
        lastLayer = layer;
        return layer;
    }

    private void sweepTemplates(long now) {
        if (templates.isEmpty()) return;
        final ObjectIterator<PartTemplate> it = templates.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().lastUsedMs > AngelicaTesrMeshCache.LRU_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    public void clear() {
        shadows.reset();
        groups.clear();
        shadowGroups.clear();
        templates.clear();
        bufferSource.discard();
        bufferSource.freeBuffers();
        active = false;
        shaderPipeline = null;
        layers.clear();
        scratchKey.set(null, null, null, 0.0f, 0.0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false);
        lastLayerTexture = null;
        lastLayerMaterial = null;
        lastLayerPass = null;
        lastLayerGlintSlot = ShaderGlint.NO_TINT;
        lastLayerCull = DrawState.DISABLED;
        lastLayerLit = false;
        lastLayer = null;
        loggedMaterialBails.clear();
        lastBoundLocation = null;
    }

    public String getDebugString() {
        final String line = String.format("Parts: %d grp, inst %d, live %d", groups.groupCount() + shadowGroups.groupCount(), parts - lastParts, liveFallbacks - lastLiveFallbacks);
        lastParts = parts;
        lastLiveFallbacks = liveFallbacks;
        return line;
    }
}
