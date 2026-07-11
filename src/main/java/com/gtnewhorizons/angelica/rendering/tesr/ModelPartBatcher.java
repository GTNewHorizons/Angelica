package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.profiling.BailClassCounts;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
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
        HURT_FLASH("tesr.bail.hurtFlash"),
        FOREIGN_PROGRAM("tesr.bail.foreignProgram");

        public static final BailReason[] VALUES = values();
        public final String plotName;

        BailReason(String plotName) {
            this.plotName = plotName;
        }
    }

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
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
    public long statBail(BailReason reason) { return bails[reason.ordinal()]; }

    private void bail(BailReason reason) {
        bails[reason.ordinal()]++;
    }

    private static final class PartTemplate {
        TemplateBuffer template;
        float scale;
        long lastUsedMs;
        boolean emptyVanillaPart;
        boolean forceStream;
    }

    private final Reference2ObjectOpenHashMap<ModelRenderer, PartTemplate> templates = new Reference2ObjectOpenHashMap<>();

    private static final class LayerKey {
        ResourceLocation texture;
        TesrMaterial material;

        LayerKey set(ResourceLocation texture, TesrMaterial material) {
            this.texture = texture;
            this.material = material;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayerKey other)) return false;
            return Objects.equals(texture, other.texture) && Objects.equals(material, other.material);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(texture) * 31 + Objects.hashCode(material);
        }
    }

    private final Object2ObjectOpenHashMap<LayerKey, RenderLayer> layers = new Object2ObjectOpenHashMap<>();
    private final LayerKey scratchKey = new LayerKey();
    private ResourceLocation lastLayerTexture;
    private TesrMaterial lastLayerMaterial;
    private RenderLayer lastLayer;

    private static ResourceLocation lastBoundLocation;
    private static int lastBoundGlId = -1;

    private ModelPartBatcher() {}

    public static void onTextureBind(ResourceLocation location, int glId) {
        lastBoundLocation = location;
        lastBoundGlId = glId;
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
        mvCaptured = false;
        activeGroups = shadow ? shadowGroups : groups;
        bufferSource.setIdKind(mode == Mode.ENTITIES ? AngelicaBufferSource.GroupIdKind.ENTITY : AngelicaBufferSource.GroupIdKind.BLOCK_ENTITY);
        activeGroups.beginPass(identity, 0, 0, 0, instancedThisCycle ? TesrBatchRenderer.INSTANCE.instancedRenderer : null, shaderPipeline);
        final long now = System.currentTimeMillis();
        if (now - lastSweepMs >= SWEEP_INTERVAL_MS) {
            lastSweepMs = now;
            groups.sweep(now);
            shadowGroups.sweep(now);
            sweepTemplates(now);
        }
    }

    public void flush() {
        if (!active) return;
        active = false;
        if (shadow) {
            bufferSource.endBatchWithType(TransparencyType.OPAQUE, activeGroups);
            bufferSource.endBatch(activeGroups);
            if (instancedThisCycle) {
                TesrBatchRenderer.INSTANCE.instancedRenderer.endFrame();
            }
            BatchingFontRenderer.flushDeferredText();
            return;
        }
        final boolean inEntityLoop = mode == Mode.ENTITIES && Iris.enabled && GbufferPrograms.isEntityLoopActive();
        final boolean wrapEntities = mode == Mode.ENTITIES && Iris.enabled && !inEntityLoop;
        if (wrapEntities) GbufferPrograms.beginEntities();
        else if (inEntityLoop) GbufferPrograms.onEntityRenderBoundary();
        try {
            bufferSource.endBatchWithType(TransparencyType.OPAQUE, activeGroups);
            bufferSource.endBatch(activeGroups);
        } finally {
            if (wrapEntities) GbufferPrograms.endEntities();
        }
        if (instancedThisCycle) {
            TesrBatchRenderer.INSTANCE.instancedRenderer.endFrame();
        }
    }

    public boolean isActive() {
        return active;
    }

    public static boolean partDraw(ModelRenderer part, float scale) {
        final ModelPartBatcher r = INSTANCE;
        if (!r.active) {
            r.bail(BailReason.INACTIVE);
            return false;
        }
        if (DisplayListManager.isRecording()) {
            r.bail(BailReason.RECORDING);
            return false;
        }
        if (!r.queuePart(part, scale)) {
            r.liveFallbacks++;
            return false;
        }
        return true;
    }

    private boolean queuePart(ModelRenderer part, float scale) {
        if (shaderPipeline != null) {
            if (GLStateManager.getActiveProgram() != shaderPipeline.getActivePassProgramId()) {
                bail(BailReason.FOREIGN_PROGRAM);
                return false;
            }
        } else if (GLStateManager.getOverlayA() != 0.0f) {
            bail(BailReason.HURT_FLASH);
            return false;
        }
        final PartTemplate entry = templateFor(part, scale);
        if (entry.emptyVanillaPart) {
            return true;
        }
        if (shadow && isDepthEqualDecalState()) {
            return true;
        }
        final TesrMaterial material = EntityMaterials.fromCurrentState();
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
        final TemplateBuffer template = entry.template;
        if (template == null) {
            bail(BailReason.TEMPLATE);
            if (Tracy.ENABLED) BailClassCounts.TEMPLATE.add(TesrAttribution.currentRenderable);
            return false;
        }

        Matrix4f texMatrix = null;
        if (texture != null) {
            final Matrix4f unitMatrix = GLStateManager.getTextures().getTextureUnitMatrix(0);
            if (!MatrixHelper.isIdentity(unitMatrix)) {
                texMatrix = texMatrixScratch.set(unitMatrix);
                if (material.isDepthEqual()) {
                    entry.forceStream = true;
                }
            }
        }
        final RenderLayer layer = layerFor(texture, material);
        final int entityId = mode == Mode.ENTITIES ? Math.max(0, CapturedRenderingState.INSTANCE.getCurrentRenderedEntity()) : CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        final Color4 color = GLStateManager.getColor();
        final int colorABGR = ColorABGR.pack(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        final int packedLight = ((int) GLSMConfig.lastBrightnessY << 16) | ((int) GLSMConfig.lastBrightnessX & 0xFFFF);
        final int mvGen = GLStateManager.mvGeneration;
        if (!mvCaptured || mvGen != lastMvGeneration) {
            modelView.set(GLStateManager.getModelViewMatrix());
            lastMvGeneration = mvGen;
            mvCaptured = true;
        }
        activeGroups.queue(template, layer, material, modelView, packedLight, colorABGR, entityId, texMatrix, entry.forceStream);
        parts++;
        return true;
    }

    private final ReferenceOpenHashSet<Class<?>> loggedMaterialBails = new ReferenceOpenHashSet<>();

    private void logMaterialBail() {
        final Class<?> cls = TesrAttribution.currentRenderable;
        if (cls == null || !loggedMaterialBails.add(cls)) return;
        final boolean textured = GLStateManager.getTextures().getTextureUnitStates(0).isEnabled();
        final BlendState blendState = GLStateManager.getBlendState();
        AngelicaMod.LOGGER.info("TESR MATERIAL bail: cls={} shadow={} textured={} texAnimated={} blend={} src={} dst={} alphaTest={} alphaFunc={} alphaRef={} depthFunc={} depthMask={}",
            cls.getName(), shadow, textured,
            textured && !MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0)),
            GLStateManager.getBlendMode().isEnabled(), blendState.getSrcRgb(), blendState.getDstRgb(),
            GLStateManager.getAlphaTest().isEnabled(),
            GLStateManager.getAlphaState().getFunction(), GLStateManager.getAlphaState().getReference(),
            GLStateManager.getDepthState().getFunc(), GLStateManager.getDepthState().isEnabled());
    }

    private static boolean isDepthEqualDecalState() {
        return GLStateManager.getDepthState().getFunc() == GL11.GL_EQUAL && !GLStateManager.getDepthState().isEnabled();
    }

    private PartTemplate templateFor(ModelRenderer part, float scale) {
        PartTemplate entry = templates.get(part);
        if (entry == null || entry.scale != scale) {
            entry = capture(part, scale, entry);
        }
        entry.lastUsedMs = lastSweepMs;
        return entry;
    }

    private PartTemplate capture(ModelRenderer part, float scale, PartTemplate reuse) {
        final PartTemplate entry = reuse != null ? reuse : new PartTemplate();
        entry.scale = scale;
        entry.template = null;
        entry.emptyVanillaPart = false;
        final List<ModelBox> boxes = part.cubeList;
        if (boxes != null && !boxes.isEmpty()) {
            final Tessellator tess = captureBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
            for (int i = 0, n = boxes.size(); i < n; i++) {
                boxes.get(i).render(tess, scale);
            }
            entry.template = captureBackend.endCaptureToTemplate();
        } else {
            entry.emptyVanillaPart = part.getClass() == ModelRenderer.class;
        }
        templates.put(part, entry);
        return entry;
    }

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material) {
        if (texture == lastLayerTexture && material == lastLayerMaterial) {
            return lastLayer;
        }
        RenderLayer layer = layers.get(scratchKey.set(texture, material));
        if (layer == null) {
            layer = RenderLayer.tesr(texture, material);
            layers.put(new LayerKey().set(texture, material), layer);
        }
        lastLayerTexture = texture;
        lastLayerMaterial = material;
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
        groups.clear();
        shadowGroups.clear();
        templates.clear();
        bufferSource.freeBuffers();
        active = false;
    }

    public String getDebugString() {
        final String line = String.format("Parts: %d grp, inst %d, live %d", groups.groupCount() + shadowGroups.groupCount(), parts - lastParts, liveFallbacks - lastLiveFallbacks);
        lastParts = parts;
        lastLiveFallbacks = liveFallbacks;
        return line;
    }
}
