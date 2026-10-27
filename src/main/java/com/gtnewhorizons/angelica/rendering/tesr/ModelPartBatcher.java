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
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorEntityIds;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import com.gtnewhorizons.angelica.mixins.interfaces.ModelBoxData;
import com.gtnewhorizons.angelica.profiling.BailClassCounts;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.items.ItemGlint;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

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
    private static final Tracy.ZoneId Z_ENTITY_DEFERRED = Tracy.zoneId("entityDeferred", Tracy.COLOR_CLIENT);

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
    private final AngelicaBufferSource entitySource = new AngelicaBufferSource();
    private final EntityShadowBatcher shadows = new EntityShadowBatcher();
    private final RetainedTesrGroups groups = new RetainedTesrGroups(bufferSource);
    private final RetainedTesrGroups entityGroups = new RetainedTesrGroups(entitySource);
    private final RetainedTesrGroups shadowGroups = new RetainedTesrGroups(bufferSource);
    private final RetainedTesrGroups shadowEntityGroups = new RetainedTesrGroups(bufferSource);
    private final RetainedTesrGroups[] allGroups = {groups, entityGroups, shadowGroups, shadowEntityGroups};
    private RetainedTesrGroups activeGroups = groups;
    private AngelicaBufferSource activeSource = bufferSource;
    private boolean entityTranslucentsHeld;
    private boolean shadow;
    private final AngelicaTesrMeshCache.GtnhMeshBackend captureBackend = new AngelicaTesrMeshCache.GtnhMeshBackend();
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f texMatrixScratch = new Matrix4f();
    private final Matrix4f replayView = new Matrix4f();
    private final EntityRenderCommands.DrawInputs drawInputs = new EntityRenderCommands.DrawInputs();
    Matrix4fc replayTransform;
    private boolean replayOffsetEnabled;
    private float replayOffsetFactor, replayOffsetUnits;
    private boolean loggedDroppedGlint;
    private int lastMvGeneration;
    private boolean mvCaptured;
    private int texGeneration;
    private boolean texCaptured;
    private boolean texIdentity;
    private boolean active;
    private int entityFireDepth;
    private Mode mode = Mode.BLOCK_ENTITIES;
    private boolean instancedThisCycle;
    private DeferredWorldRenderingPipeline shaderPipeline;
    private long lastSweepMs;
    private RenderLayer armorGlintLayer;
    private final Matrix4f armorGlintTexture = new Matrix4f();
    private int armorGlintColor, armorGlintLight, armorGlintOverlay;
    private static final int VANILLA_GLINT_COLOR = AngelicaBufferSource.packAbgr(0.5F * 0.76F, 0.25F * 0.76F, 0.8F * 0.76F, 1.0F);

    long parts;
    long liveFallbacks;
    private long armorBaseReuses, glintPassReuses, armorLoopSkips, armorSectionSkips;
    private final long[] bails = new long[BailReason.VALUES.length];
    private long lastParts, lastLiveFallbacks;

    public long statParts() { return parts; }
    public long statArmorBaseReuses() { return armorBaseReuses; }
    public long statGlintPassReuses() { return glintPassReuses; }
    public long statArmorLoopSkips() { return armorLoopSkips; }
    public long statArmorSectionSkips() { return armorSectionSkips; }
    public long statLiveFallbacks() { return liveFallbacks; }
    public long statInstancedDraws() { return sumGroups(g -> g.instancedDraws); }
    public long statInstancedInstances() { return sumGroups(g -> g.instancedInstances); }
    public long statCubeInstances() { return sumGroups(g -> g.cubeInstances); }
    public long statRedrawnInstances() { return sumGroups(g -> g.redrawnInstances); }
    public long statTexMatrixRuns() { return sumGroups(g -> g.texMatrixRuns); }

    private long sumGroups(ToLongFunction<RetainedTesrGroups> stat) {
        long sum = 0;
        for (RetainedTesrGroups group : allGroups) sum += stat.applyAsLong(group);
        return sum;
    }
    public long statBail(BailReason reason) { return bails[reason.ordinal()]; }
    public long statShadowQuads() { return shadows.statQuads(); }
    public long statShadowDraws() { return shadows.statDraws(); }

    private void bail(BailReason reason) {
        bails[reason.ordinal()]++;
        BatchEligibility.onBail(reason);
    }

    private static final class PartTemplate {
        TemplateBuffer template;
        CubeParams[] cubes;
        boolean cubesInsideTexture;
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
        boolean lightmap;
        boolean afterGlint;

        boolean matches(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit, boolean lightmap, boolean afterGlint) {
            return this.texture == texture && this.material == material && pass == this.pass && this.offsetFactor == offsetFactor
                && this.offsetUnits == offsetUnits && this.glintSlot == glintSlot && this.cull == cull && this.lit == lit && this.lightmap == lightmap
                && this.afterGlint == afterGlint;
        }

        LayerKey set(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit, boolean lightmap, boolean afterGlint) {
            this.texture = texture;
            this.material = material;
            this.pass = pass;
            this.offsetFactor = offsetFactor;
            this.offsetUnits = offsetUnits;
            this.glintSlot = glintSlot;
            this.cull = cull;
            this.lit = lit;
            this.lightmap = lightmap;
            this.afterGlint = afterGlint;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayerKey other)) return false;
            return Objects.equals(texture, other.texture) && Objects.equals(material, other.material)
                && pass == other.pass
                && Float.floatToIntBits(offsetFactor) == Float.floatToIntBits(other.offsetFactor)
                && Float.floatToIntBits(offsetUnits) == Float.floatToIntBits(other.offsetUnits)
                && glintSlot == other.glintSlot && cull == other.cull && lit == other.lit && lightmap == other.lightmap && afterGlint == other.afterGlint;
        }

        @Override
        public int hashCode() {
            int h = (Objects.hashCode(texture) * 31 + Objects.hashCode(material)) * 31 + System.identityHashCode(pass);
            h = h * 31 + Float.floatToIntBits(offsetFactor);
            h = h * 31 + Float.floatToIntBits(offsetUnits);
            h = h * 31 + glintSlot;
            h = h * 31 + cull;
            h = h * 31 + (lit ? 1 : 0);
            h = h * 31 + (lightmap ? 1 : 0);
            h = h * 31 + (afterGlint ? 1 : 0);
            return h;
        }
    }

    private final Object2ObjectOpenHashMap<LayerKey, RenderLayer> layers = new Object2ObjectOpenHashMap<>();
    private final LayerKey scratchKey = new LayerKey();
    private static final int RECENT_LAYERS = 8;
    private final LayerKey[] recentKeys = new LayerKey[RECENT_LAYERS];
    private final RenderLayer[] recentLayers = new RenderLayer[RECENT_LAYERS];
    private int recentNext;
    private RenderLayer lastLayer;

    private static ResourceLocation lastBoundLocation;
    private static int lastBoundGlId = -1;
    private static int lastBoundGlintSlot = ShaderGlint.NO_TINT;

    static final class BoundTexture {
        ResourceLocation location;
        int id, glintSlot;

        void capture() {
            id = GLSMTextures.getBoundTextureForServerState(0);
            location = id == lastBoundGlId ? lastBoundLocation : null;
            glintSlot = id == lastBoundGlId ? lastBoundGlintSlot : ShaderGlint.NO_TINT;
        }

        void apply() {
            if (GLSMTextures.getBoundTextureForServerState(0) != id) return;
            lastBoundLocation = location;
            lastBoundGlId = id;
            lastBoundGlintSlot = glintSlot;
        }
    }

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
        BatchStateFallback.install();
        this.mode = mode;
        this.shadow = shadow;
        instancedThisCycle = TesrBatchRenderer.instancedCapable();
        shaderPipeline = TesrBatchRenderer.deferredPipeline();
        if (!AngelicaConfig.enableEntityBatching || !(instancedThisCycle || shaderPipeline != null)) {
            active = false;
            shaderPipeline = null;
            return;
        }
        final boolean entityPass = mode == Mode.ENTITIES && !shadow;
        if (entityPass && entityTranslucentsHeld) {
            discardHeldEntities();
        }
        // Held entity translucents draw after deferred and can still sample regions released before them
        if (!entityTranslucentsHeld) recycleAtlas();
        active = true;
        final long nowMs = System.currentTimeMillis();
        mvCaptured = false;
        texCaptured = false;
        final boolean replayEntities = mode == Mode.ENTITIES && AngelicaConfig.entityShadowReplayEnabled();
        activeGroups = shadow ? (replayEntities ? shadowEntityGroups : shadowGroups) : entityPass ? entityGroups : groups;
        activeSource = entityPass ? entitySource : bufferSource;
        activeSource.setIdKind(mode == Mode.ENTITIES ? AngelicaBufferSource.GroupIdKind.ENTITY : AngelicaBufferSource.GroupIdKind.BLOCK_ENTITY);
        activeSource.capturePassLighting();
        BatchDrawDefaults.capturePassFramebuffer();
        activeGroups.beginPass(identity, 0, 0, 0, instancedThisCycle ? TesrBatchRenderer.INSTANCE.instancedRenderer : null, shaderPipeline);
        if (replayEntities && shaderPipeline != null) EntityShadowReplay.beginPass(shadow, MinecraftForgeClient.getRenderPass());
        if (nowMs - lastSweepMs >= SWEEP_INTERVAL_MS) {
            lastSweepMs = nowMs;
            for (RetainedTesrGroups group : allGroups) group.sweep(nowMs);
            sweepTemplates(nowMs);
        }
    }

    public void flush() {
        BatchStateGuard.suspend();
        try {
            flushNow(true);
        } finally {
            BatchStateGuard.resume();
        }
    }

    void flushForStateChange() {
        if (!active) return;
        flushNow(false);
        active = true;
        mvCaptured = false;
        texCaptured = false;
        activeGroups.beginPass(identity, 0, 0, 0, instancedThisCycle ? TesrBatchRenderer.INSTANCE.instancedRenderer : null, shaderPipeline);
    }

    private void flushNow(boolean allowHold) {
        if (!active) return;
        active = false;
        final boolean hold = allowHold && mode == Mode.ENTITIES && !shadow && MinecraftForgeClient.getRenderPass() == 0
            && shaderPipeline != null && shaderPipeline.shouldSeparateEntityDraws();
        if (shadow) {
            drawBatches(false);
        } else {
            final boolean inEntityLoop = mode == Mode.ENTITIES && Iris.enabled && GbufferPrograms.isEntityLoopActive();
            final boolean wrapEntities = mode == Mode.ENTITIES && Iris.enabled && !inEntityLoop;
            if (wrapEntities) GbufferPrograms.beginEntities();
            else if (inEntityLoop) GbufferPrograms.onEntityRenderBoundary();
            try {
                shadows.flush(activeSource);
                drawBatches(hold);
            } finally {
                if (wrapEntities) GbufferPrograms.endEntities();
            }
        }
        if (!TesrBatchRenderer.INSTANCE.hasPendingGeometry()) {
            BatchingFontRenderer.flushDeferredText();
        }
    }

    private void drawBatches(boolean hold) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_ENTITY_LAYER_LOOP);
        try {
            activeSource.endBatchWithType(TransparencyType.OPAQUE, activeGroups);
            if (hold) {
                activeSource.endBatchWithType(TransparencyType.WATER_MASK, activeGroups);
                activeSource.pauseBatch();
                entityTranslucentsHeld = true;
            } else {
                activeSource.endBatch(activeGroups);
            }
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    public void flushEntitiesAfterDeferred() {
        if (!entityTranslucentsHeld) return;
        entityTranslucentsHeld = false;
        BatchStateGuard.suspend();
        if (Tracy.ENABLED) Tracy.beginZone(Z_ENTITY_DEFERRED);
        try {
            final EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
            final int deferredDepth = GLSMAttribStack.pushState(StateSet.BATCH);
            try {
                GLSMDepthStencilAlpha.glDepthMask(true);
                GLSMDepthStencilAlpha.enableDepthTest();
                GLSMBlend.enableBlend();
                GLSMBlend.defaultBlendFunc();
                entityRenderer.enableLightmap(0);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
                final boolean wrap = Iris.enabled && GbufferPrograms.getCurrentPhase() == WorldRenderingPhase.NONE;
                if (wrap) GbufferPrograms.beginEntities();
                try {
                    entitySource.endBatch(entityGroups);
                    recycleAtlas();
                    AngelicaBufferSource.rebindPass();
                } finally {
                    if (wrap) GbufferPrograms.endEntities();
                    entityRenderer.disableLightmap(0);
                }
            } finally {
                GLSMAttribStack.popStateTo(deferredDepth);
            }
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
            BatchStateGuard.resume();
        }
    }

    private void discardHeldEntities() {
        entityTranslucentsHeld = false;
        entitySource.discard();
        recycleAtlas();
    }

    private static void recycleAtlas() {
        if (AngelicaConfig.enableEntityTextureAtlas) {
            EntityTextureAtlas.INSTANCE.recycleReleasedRegions();
        } else {
            EntityTextureAtlas.INSTANCE.freePages();
        }
    }

    public boolean isActive() {
        return active;
    }

    boolean hasHeldEntities() {
        return entityTranslucentsHeld;
    }

    boolean hasQueuedGeometry() {
        return active && (activeSource.hasPendingLayers() || shadows.hasPendingGeometry());
    }

    public boolean beginGlintCapture(GlintCapture capture) {
        return canCaptureGlint() && capture.begin(activeGroups);
    }

    public boolean canCaptureGlint() {
        return shaderPipeline == null && canQueueGlint();
    }

    /** Skipped glint blocks queue each layer as its own instanced draw, so a shader pack only needs its pass program current. */
    public boolean canQueueGlint() {
        return !shadow && canBatchGlint();
    }

    private boolean cannotQueueOrRecordGlint() {
        return shadow && (mode != Mode.ENTITIES || !EntityShadowReplay.isRecording()) || !canBatchGlint();
    }

    private boolean canBatchGlint() {
        return active && instancedThisCycle && BatchEligibility.batchingAllowed() && !GLStateManager.isRecordingDisplayList() && passProgramBound();
    }

    private boolean passProgramBound() {
        return GLSMPrograms.getActiveProgram() == (shaderPipeline != null ? shaderPipeline.getActivePassProgramId() : 0);
    }

    public boolean replayGlint(GlintCapture capture) {
        return replayGlint(capture, GLSMTextures.getTextures().getTextureUnitMatrix(0));
    }

    public boolean queueSecondArmorGlint(GlintCapture capture) {
        final Matrix4f second = GlintClock.secondArmorMatrix(GLSMTextures.getTextures().getTextureUnitMatrix(0));
        if (second == null || !replayGlint(capture, second)) return false;
        armorLoopSkips++;
        return true;
    }

    private boolean replayGlint(GlintCapture capture, Matrix4f textureMatrix) {
        if (!active || shaderPipeline != null || shadow || !BatchEligibility.batchingAllowed() || GLSMPrograms.getActiveProgram() != 0 || GLStateManager.isRecordingDisplayList()) return false;
        if (!capture.replay(activeGroups, textureMatrix)) return false;
        glintPassReuses++;
        BatchEligibility.onPartQueued();
        return true;
    }

    public boolean beginArmorCapture(GlintCapture capture) {
        if (cannotQueueOrRecordGlint() || !capture.beginBase(activeGroups, currentOverlay())) return false;
        if (shadow) capture.beginShadowReplay();
        return true;
    }

    boolean canReplayGlint(TesrMaterial material) {
        return entityPassActive() && instancedThisCycle && activeGroups.glintCapture == null
            && activeGroups.canCopyInstances(material);
    }

    boolean canInstanceBaseAndGlint(EntityRenderCommands.Model base, boolean glintLightmap) {
        final EntityRenderCommands.DrawInputs inputs = base.inputs;
        if (!activeGroups.canCopyInstances(inputs.material)) return false;
        if (shaderPipeline == null) return true;
        final RenderCondition condition = inputs.pass.entityCondition(inputs.material.transparency() == TesrMaterial.Transparency.TRANSLUCENT);
        if (condition == null) return false;
        final Instancing kind = base.drawsCubes() ? Instancing.CUBE : Instancing.TEMPLATE;
        return shaderPipeline.hasInstancedVariant(condition, InputAvailability.of(inputs.texture != null, inputs.lightmap), kind)
            && shaderPipeline.hasInstancedVariant(RenderCondition.GLINT, InputAvailability.of(true, glintLightmap), kind);
    }

    public boolean reuseArmorBase(GlintCapture base, GlintCapture glint) {
        if (!canCaptureGlint() || !glint.beginTrusted(activeGroups)) return false;
        final boolean copied;
        try {
            copied = base.copyBase(this, activeGroups);
        } finally {
            glint.end();
        }
        if (!copied) {
            glint.reset();
            return false;
        }
        armorBaseReuses++;
        return true;
    }

    public boolean queueSkippedArmorGlint(GlintCapture base, ResourceLocation glintTexture) {
        if (cannotQueueOrRecordGlint() || GLSMTextures.getActiveTextureUnit() != 0 || !GLSMTextures.getTextures().getTextureUnitStates(0).isEnabled()) return false;
        if (!(shadow ? base.recordBothLayers(this, activeGroups, glintTexture) : base.queueBothLayers(this, activeGroups, glintTexture))) return false;
        if (!shadow) {
            armorBaseReuses++;
            glintPassReuses++;
            armorSectionSkips++;
        }
        BatchEligibility.onPartQueued();
        return true;
    }

    public boolean queueSkippedItemGlint(TemplateBuffer template, TesrMaterial material) {
        if (cannotQueueOrRecordGlint() || GLSMTextures.getActiveTextureUnit() != 0 || !activeGroups.canCopyInstances(material) || !MatrixHelper.isIdentity(GLSMTextures.getTextures().getTextureUnitMatrix(0))) return false;
        final EntityRenderCommands.DrawInputs inputs = glintInputs(ItemGlint.texture(), material, ShaderGlint.NO_TINT, false);
        if (shadow) {
            if (material == EntityMaterials.ITEM_GLINT && !ItemGlint.canResetStencil()) return false;
            final EntityRenderCommands.ItemGlint entry = EntityShadowReplay.recordItemGlint(currentModelView(), inputs);
            if (entry == null) return false;
            GLStateManager.requireExitDrawState(false);
            entry.instance.set(template, GLSMConfig.packedLastBrightness(), VANILLA_GLINT_COLOR, currentOverlay(), currentEntityInfo(0));
            return true;
        }
        final RenderLayer layer = layerFor(ItemGlint.texture(), inputs);
        activeGroups.queueGlintLayers(template, layer, material, currentModelView(), GLSMConfig.packedLastBrightness(), VANILLA_GLINT_COLOR, currentOverlay(), currentEntityInfo(0),
            ItemGlint.firstMatrix(), ItemGlint.secondMatrix());
        parts++;
        glintPassReuses++;
        return true;
    }

    boolean recordArmorGlint(int start, int end, ResourceLocation texture) {
        if (EntityShadowReplay.recordArmorGlint(start, end, glintInputs(texture, EntityMaterials.GLINT, ShaderGlint.NO_TINT, false)) == null) return false;
        GLStateManager.requireExitDrawState(false);
        return true;
    }

    private EntityRenderCommands.DrawInputs glintInputs(ResourceLocation texture, TesrMaterial material, int glintSlot, boolean lit) {
        return liveInputs(texture, 0, material, PassOverride.NONE, glintSlot, DrawState.liveCull(material), lit, DrawState.liveLightmap(), false);
    }

    private EntityRenderCommands.DrawInputs liveInputs(ResourceLocation texture, int textureId, TesrMaterial material, PassOverride pass,
        int glintSlot, int cull, boolean lit, boolean lightmap, boolean afterGlint) {
        final PolygonState polygon = GLSMRasterizer.getPolygonState();
        return drawInputs.set(texture, textureId, material, pass, GLSMRasterizer.getPolygonOffsetFillState().isEnabled(),
            polygon.getOffsetFactor(), polygon.getOffsetUnits(), glintSlot, cull, lit, lightmap, afterGlint);
    }

    private Matrix4f currentModelView() {
        final int mvGen = GLSMMatrices.getMvGeneration();
        if (!mvCaptured || mvGen != lastMvGeneration) {
            modelView.set(GLSMMatrices.getModelViewMatrix());
            lastMvGeneration = mvGen;
            mvCaptured = true;
        }
        return modelView;
    }

    private long currentEntityInfo(int item) {
        return shaderPipeline != null ? InstancedAttribs.packEntityInfo(CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity(), item) : 0L;
    }

    int currentOverlay() {
        return shaderPipeline != null ? AngelicaBufferSource.packEntityColor(CapturedRenderingState.INSTANCE.getCurrentEntityColor())
            : AngelicaBufferSource.packAbgr(GLSMCurrentAttribs.getOverlayR(), GLSMCurrentAttribs.getOverlayG(), GLSMCurrentAttribs.getOverlayB(), GLSMCurrentAttribs.getOverlayA());
    }

    boolean prepareArmorGlint() {
        if (GLSMTextures.getBoundTextureForServerState() != lastBoundGlId || lastBoundLocation == null) return false;
        final Color4 color = GLSMCurrentAttribs.getColor();
        setArmorGlint(lastBoundLocation, lastBoundGlintSlot, DrawState.liveLit(EntityMaterials.GLINT), GLSMTextures.getTextures().getTextureUnitMatrix(0),
            AngelicaBufferSource.packAbgr(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        return true;
    }

    boolean prepareSkippedArmorGlint(ResourceLocation glintTexture) {
        setArmorGlint(glintTexture, ShaderGlint.NO_TINT, false, GlintClock.firstArmorMatrix(), VANILLA_GLINT_COLOR);
        return true;
    }

    private void setArmorGlint(ResourceLocation texture, int glintSlot, boolean lit, Matrix4f textureMatrix, int color) {
        armorGlintLayer = layerFor(texture, glintInputs(texture, EntityMaterials.GLINT, glintSlot, lit));
        armorGlintTexture.set(textureMatrix);
        armorGlintColor = color;
        armorGlintLight = GLSMConfig.packedLastBrightness();
        armorGlintOverlay = currentOverlay();
    }

    void copyArmorGlint(RetainedTesrGroups.InstanceColumns base, int start, int end, boolean inPlace) {
        if (inPlace) {
            activeGroups.referenceInstances(base, start, end, armorGlintLayer, EntityMaterials.GLINT, armorGlintColor, armorGlintTexture);
        } else {
            activeGroups.copyInstances(base, start, end, armorGlintLayer, EntityMaterials.GLINT, armorGlintLight, armorGlintColor, armorGlintOverlay, armorGlintTexture);
        }
        parts += end - start;
    }

    void queueArmorGlintLayers(RetainedTesrGroups.InstanceColumns base, RetainedTesrGroups.TexRun baseRun, int start, int end) {
        activeGroups.referenceGlintLayers(base, baseRun, start, end, armorGlintLayer, EntityMaterials.GLINT, armorGlintColor, GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix());
        parts += end - start;
    }

    public boolean entityPassActive() {
        return entitiesActive() && !shadow;
    }

    boolean canReplayEntity() {
        return entityPassActive() && passProgramBound();
    }

    public boolean entitiesActive() {
        return active && mode == Mode.ENTITIES;
    }

    public boolean isShadowPass() {
        return active && shadow;
    }

    boolean recordsEntitySubmissions(RetainedTesrGroups groups) {
        return active && shadow && mode == Mode.ENTITIES && activeGroups == groups;
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
            EntityShadowReplay.rejectShadowOnly();
            return true;
        }
        return queueTemplate(entry.template, entry.cubes, entry.cubesInsideTexture, scale, null, false);
    }

    public boolean queueTemplate(TemplateBuffer template, TesrMaterial material) {
        return queueTemplate(template, null, false, 1.0f, material, false);
    }

    public boolean queueTemplate(TemplateBuffer template, TesrMaterial material, boolean afterGlint) {
        return queueTemplate(template, null, false, 1.0f, material, afterGlint);
    }

    private boolean queueTemplate(TemplateBuffer template, CubeParams[] cubes, boolean cubesInsideTexture, float scale, TesrMaterial explicitMaterial, boolean afterGlint) {
        if (!active) {
            bail(BailReason.INACTIVE);
            return false;
        }
        if (shaderPipeline != null && GLSMPrograms.getActiveProgram() != shaderPipeline.getActivePassProgramId()) {
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
            if (GLSMTextures.getBoundTextureForServerState() != lastBoundGlId) {
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

        GLStateManager.requireExitDrawState(false);
        final Matrix4f texMatrix = texture != null && !unitTexIdentity() ? texMatrixScratch : null;
        final PassOverride pass = shaderPipeline != null ? PassOverride.capture() : PassOverride.NONE;
        final int glintSlot = material.special().isGlint() ? lastBoundGlintSlot : ShaderGlint.NO_TINT;
        final EntityRenderCommands.DrawInputs inputs = liveInputs(texture, lastBoundGlId, material, pass, glintSlot,
            DrawState.liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap(), afterGlint);
        final EntityTextureAtlas.Slot atlasSlot = texMatrix == null && cubesInsideTexture ? atlasSlotFor(material) : null;
        final RenderLayer layer = layerFor(atlasSlot != null ? Objects.requireNonNull(atlasSlot.page).location : texture, inputs);
        // Entities nested inside a TESR (mob spawner, OpenBlocks trophy) run in the block entity pass but carry an entity id
        final int entityId = groupId(shaderPipeline != null, mode == Mode.ENTITIES || pass.isEntityPhase(), CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
        final Color4 color = GLSMCurrentAttribs.getColor();
        final int colorABGR = AngelicaBufferSource.packAbgr(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        final int overlayABGR = currentOverlay();
        final long entityInfo = currentEntityInfo(CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
        final int packedLight = GLSMConfig.packedLastBrightness();
        final Matrix4f modelView = currentModelView();
        activeGroups.queueAndRecord(template, cubes, scale, layer, modelView, packedLight, colorABGR, overlayABGR, entityInfo,
            entityId, texMatrix, atlasSlot, cubesInsideTexture, inputs);
        parts++;
        return true;
    }

    private EntityTextureAtlas.Slot atlasSlotFor(TesrMaterial material) {
        return atlasSlotFor(material, lastBoundGlId);
    }

    private EntityTextureAtlas.Slot atlasSlotFor(TesrMaterial material, int textureId) {
        if (!AngelicaConfig.enableEntityTextureAtlas) return null;
        // Glint, overlay and custom shaders sample their own textures
        if (material.special() != TesrMaterial.SpecialRender.NONE || material.shader() != null || material.isUnfilteredAtlas() || material == EntityMaterials.OVERLAY) return null;
        if (!activeGroups.instancesCubes(material)) return null;
        if (shaderPipeline != null && !shaderPipeline.hasInstancedVariant(Instancing.CUBE)) return null;
        return EntityTextureAtlas.INSTANCE.slotFor(textureId, shaderPipeline != null);
    }

    void beginReplay(Matrix4fc mainFromShadow) {
        replayTransform = mainFromShadow;
        final PolygonState polygon = GLSMRasterizer.getPolygonState();
        replayOffsetEnabled = GLSMRasterizer.getPolygonOffsetFillState().isEnabled();
        replayOffsetFactor = polygon.getOffsetFactor();
        replayOffsetUnits = polygon.getOffsetUnits();
    }

    private RenderLayer replayLayer(EntityRenderCommands.Draw entry, EntityTextureAtlas.Slot slot) {
        final EntityRenderCommands.DrawInputs inputs = entry.inputs;
        final boolean enabled = inputs.inheritedOffsetEnable ? replayOffsetEnabled : inputs.offsetEnabled;
        final float factor = inputs.inheritedOffsetValues ? replayOffsetFactor : inputs.offsetFactor;
        final float units = inputs.inheritedOffsetValues ? replayOffsetUnits : inputs.offsetUnits;
        final boolean lit = inputs.inheritedLighting ? DrawState.liveLit(inputs.material) : inputs.lit;
        return layerFor(slot != null ? Objects.requireNonNull(slot.page).location : inputs.texture,
            inputs.material, inputs.pass, enabled, factor, units, inputs.glintSlot, inputs.cull, lit, inputs.lightmap, inputs.afterGlint);
    }

    void replay(EntityRenderCommands.Model entry) {
        final RetainedTesrGroups.InstanceRange replayed = entry.glint != null ? entry.replayed : null;
        final TesrMaterial material = entry.inputs.material;
        // begin() may have recycled the shadow pass's atlas region.
        // A reused base upload must keep the original UVs for the glint texture.
        final EntityTextureAtlas.Slot slot = (replayed == null || !replayed.reuseUpload) && !entry.hasTextureMatrix && entry.cubesInsideTexture
            ? atlasSlotFor(material, entry.inputs.textureId) : null;
        final RenderLayer layer = replayLayer(entry, slot);
        final Matrix4f texMatrix = entry.hasTextureMatrix ? entry.textureMatrix : null;
        if (entry.source != null) {
            activeGroups.replayInstances(entry.source, entry.start, entry.end, layer, material, entry.entityId,
                replayTransform, texMatrix, slot, replayed);
            parts += entry.end - entry.start;
        } else {
            replayTransform.mul(entry.modelView, replayView);
            final EntityRenderCommands.Instance instance = entry.instance;
            activeGroups.queueReplay(instance.template, entry.cubes, entry.scale, layer, material, replayView,
                instance.light, instance.color, instance.overlay, instance.entityInfo, entry.entityId, texMatrix, slot, replayed);
            parts++;
        }
    }

    void replay(EntityRenderCommands.ModelGlint entry) {
        final RenderLayer layer = replayLayer(entry, null);
        for (EntityRenderCommands.Model base = entry.base; base != null; base = base.nextGlintBase) {
            final RetainedTesrGroups.InstanceRange range = base.replayed;
            if (glintBaseMissing(range)) continue;
            activeGroups.referenceGlintLayers(range.columns, range.run, range.start, range.end, layer, entry.inputs.material,
                VANILLA_GLINT_COLOR, GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix());
            parts += range.end - range.start;
        }
    }

    void replay(EntityRenderCommands.ItemGlint entry) {
        final RetainedTesrGroups.InstanceRange base = entry.base.replayed;
        if (glintBaseMissing(base)) return;
        final RenderLayer layer = replayLayer(entry, null);
        base.columns.pose(base.end - 1, replayView);
        final EntityRenderCommands.Instance instance = entry.instance;
        activeGroups.queueGlintLayers(instance.template, layer, entry.inputs.material, replayView,
            instance.light, instance.color, instance.overlay, instance.entityInfo, ItemGlint.firstMatrix(), ItemGlint.secondMatrix());
        parts++;
    }

    private boolean glintBaseMissing(RetainedTesrGroups.InstanceRange base) {
        if (base.columns != null) return false;
        if (!loggedDroppedGlint) {
            loggedDroppedGlint = true;
            AngelicaMod.LOGGER.warn("Entity shadow replay dropped a glint render because its base was not drawn instanced");
        }
        return true;
    }

    void replay(EntityRenderCommands.Stream entry) {
        final RenderLayer layer = replayLayer(entry, null);
        replayTransform.mul(entry.modelView, replayView);
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushEntityState();
        try {
            state.setCurrentEntityAndItem(entry.entity, entry.item);
            state.setCurrentBlockEntity(entry.blockEntity);
            state.setCurrentEntityColor(entry.red, entry.green, entry.blue, entry.alpha);
            activeGroups.streamVertices(layer, entry.entityId, entry.vertices, entry.vertexCount, replayView);
        } finally {
            state.popEntityState();
        }
    }

    private enum TessellatorRoute { DRAW_NOW, DRAW_NOW_EXPECTED, QUEUE }

    public static int drawTessellator(Tessellator tess) {
        switch (INSTANCE.routeTessellatorDraw(tess)) {
            case QUEUE -> {
                tess.isDrawing = false;
                final int result = tess.rawBufferIndex * 4;
                tess.reset();
                return result;
            }
            case DRAW_NOW_EXPECTED -> {
                BatchEligibility.beginExpectedDraws(GLSMDraws.drawCalls);
                try {
                    return TessellatorStreamingDrawer.draw(tess);
                } finally {
                    BatchEligibility.endExpectedDraws(GLSMDraws.drawCalls);
                }
            }
            default -> {
                return TessellatorStreamingDrawer.draw(tess);
            }
        }
    }

    public void beginEntityFire() { entityFireDepth++; }
    public void endEntityFire() { entityFireDepth--; }

    private TessellatorRoute routeTessellatorDraw(Tessellator tess) {
        if (!active || !GLStateManager.isMainThread()) return TessellatorRoute.DRAW_NOW;
        final boolean entityFire = entityFireDepth != 0 && mode == Mode.ENTITIES;
        if (!entityFire && (mode != Mode.BLOCK_ENTITIES || !BatchEligibility.insideRenderer())) return TessellatorRoute.DRAW_NOW;
        if (!tess.isDrawing || tess.drawMode != GL11.GL_QUADS || tess.vertexCount == 0 || (tess.vertexCount & 3) != 0) return TessellatorRoute.DRAW_NOW;
        if (shaderPipeline != null && TessellatorEntityIds.hasIdChanges(tess)) return TessellatorRoute.DRAW_NOW;
        if (DisplayListManager.isRecording() || !GLSMDepthStencilAlpha.getDepthTest().isEnabled() || !unitTexIdentity()) return TessellatorRoute.DRAW_NOW;
        if (!passProgramBound()) return TessellatorRoute.DRAW_NOW;
        final TesrMaterial material = EntityMaterials.fromCurrentState(true);
        if (material == null || material == EntityMaterials.OVERLAY) return TessellatorRoute.DRAW_NOW;
        if (lastBoundLocation == null || GLSMTextures.getBoundTextureForServerState() != lastBoundGlId) return TessellatorRoute.DRAW_NOW;
        if (!entityFire && !BatchEligibility.batchingAllowed()) return TessellatorRoute.DRAW_NOW_EXPECTED;

        GLStateManager.requireExitDrawState(false);
        final PassOverride pass = shaderPipeline != null ? PassOverride.capture() : PassOverride.NONE;
        final EntityRenderCommands.DrawInputs inputs = liveInputs(lastBoundLocation, lastBoundGlId, material, pass, ShaderGlint.NO_TINT,
            DrawState.liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap(), false);
        final RenderLayer layer = layerFor(lastBoundLocation, inputs);
        // Flames carry entity_flame, which some packs may voxelize as a light source in the shadow pass
        final int groupId = groupId(shaderPipeline != null, mode == Mode.ENTITIES || pass.isEntityPhase(), CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(), CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
        TessellatorStreamingDrawer.fillMissingAttributes(tess);
        final Matrix4f modelView = currentModelView();
        if (entityFire) {
            activeGroups.streamAndRecord(layer, groupId, tess.rawBuffer, tess.vertexCount, modelView, inputs);
        } else {
            activeGroups.streamVertices(layer, groupId, tess.rawBuffer, tess.vertexCount, modelView);
        }
        return TessellatorRoute.QUEUE;
    }

    private final ReferenceOpenHashSet<Class<?>> loggedMaterialBails = new ReferenceOpenHashSet<>();
    private final BlendState bailBlend = new BlendState();
    private final AlphaState bailAlpha = new AlphaState();

    private void logMaterialBail() {
        final Class<?> cls = TesrAttribution.currentRenderable;
        if (cls == null || !loggedMaterialBails.add(cls)) return;
        final boolean textured = GLSMTextures.getTextures().getTextureUnitStates(0).isEnabled();
        final BlendState blendState = GLSMBlend.getEffectiveBlendState(bailBlend);
        final AlphaState alphaState = GLSMDepthStencilAlpha.getEffectiveAlphaState(bailAlpha);
        AngelicaMod.LOGGER.info("TESR MATERIAL bail: cls={} shadow={} textured={} texAnimated={} blend={} src={} dst={} alphaTest={} alphaFunc={} alphaRef={} depthFunc={} depthMask={}",
            cls.getName(), shadow, textured, textured && !unitTexIdentity(), GLSMBlend.isEffectiveBlendEnabled(), blendState.getSrcRgb(),
            blendState.getDstRgb(), GLSMDepthStencilAlpha.isEffectiveAlphaTestEnabled(), alphaState.getFunction(), alphaState.getReference(),
            GLSMDepthStencilAlpha.getDepthState().getFunc(), GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled());
    }

    static int groupId(boolean piped, boolean entityPhase, int entityId, int blockEntityId) {
        if (!piped) return 0;
        return entityPhase ? entityId : blockEntityId;
    }

    private boolean unitTexIdentity() {
        final int generation = GLSMMatrices.getTexMatrixGeneration();
        if (!texCaptured || generation != texGeneration) {
            final Matrix4f unitMatrix = GLSMTextures.getTextures().getTextureUnitMatrix(0);
            texIdentity = MatrixHelper.isIdentity(unitMatrix);
            if (!texIdentity) texMatrixScratch.set(unitMatrix);
            texGeneration = generation;
            texCaptured = true;
        }
        return texIdentity;
    }

    private static boolean isDepthEqualDecalState() {
        return GLSMDepthStencilAlpha.getDepthState().getFunc() == GL11.GL_EQUAL && !GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled();
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
        entry.cubesInsideTexture = false;
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
        entry.cubesInsideTexture = CubeParams.allInsideTexture(cubes);
    }

    private RenderLayer layerFor(ResourceLocation texture, EntityRenderCommands.DrawInputs inputs) {
        return layerFor(texture, inputs.material, inputs.pass, inputs.offsetEnabled, inputs.offsetFactor, inputs.offsetUnits,
            inputs.glintSlot, inputs.cull, inputs.lit, inputs.lightmap, inputs.afterGlint);
    }

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material, PassOverride pass, boolean offsetEnabled, float offsetFactor, float offsetUnits, int glintSlot, int cullCode, boolean lit, boolean lightmap, boolean afterGlint) {
        return layerFor(texture, material, pass, offsetEnabled ? offsetFactor : 0, offsetEnabled ? offsetUnits : 0, glintSlot, cullCode, lit, lightmap, afterGlint);
    }

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cullCode, boolean lit, boolean lightmap, boolean afterGlint) {
        for (int i = 0; i < RECENT_LAYERS; i++) {
            final RenderLayer recent = recentLayers[i];
            if (recent != null && recentKeys[i].matches(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit, lightmap, afterGlint)) {
                return lastLayer = recent;
            }
        }
        RenderLayer layer = layers.get(scratchKey.set(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit, lightmap, afterGlint));
        if (layer == null) {
            layer = RenderLayer.tesr(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit, lightmap, afterGlint);
            layers.put(new LayerKey().set(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit, lightmap, afterGlint), layer);
        }
        if (recentKeys[recentNext] == null) recentKeys[recentNext] = new LayerKey();
        recentKeys[recentNext].set(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cullCode, lit, lightmap, afterGlint);
        recentLayers[recentNext] = layer;
        recentNext = (recentNext + 1) % RECENT_LAYERS;
        return lastLayer = layer;
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
        if (AngelicaConfig.entityShadowReplayEnabled()) EntityShadowReplay.clear();
        entityFireDepth = 0;
        shadows.reset();
        for (RetainedTesrGroups group : allGroups) group.clear();
        templates.clear();
        entitySource.discard();
        bufferSource.discard();
        bufferSource.freeBuffers();
        entitySource.freeBuffers();
        activeGroups = groups;
        activeSource = bufferSource;
        entityTranslucentsHeld = false;
        active = false;
        shaderPipeline = null;
        layers.clear();
        scratchKey.set(null, null, null, 0.0f, 0.0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false, false, false);
        Arrays.fill(recentLayers, null);
        recentNext = 0;
        lastLayer = null;
        loggedMaterialBails.clear();
        lastBoundLocation = null;
    }

    public String getDebugString() {
        final String line = String.format("Parts: %d grp, inst %d, live %d", sumGroups(RetainedTesrGroups::groupCount), parts - lastParts, liveFallbacks - lastLiveFallbacks);
        lastParts = parts;
        lastLiveFallbacks = liveFallbacks;
        return line;
    }
}
