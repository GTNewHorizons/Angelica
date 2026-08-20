package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

public final class TesrBatchRenderer {

    public static final TesrBatchRenderer INSTANCE = new TesrBatchRenderer();

    public static final int PASS_MAIN_0 = 0;
    public static final int PASS_MAIN_1 = 1;
    public static final int PASS_SHADOW = 2;
    private static final int PASS_COUNT = 3;

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private static final Tracy.ZoneId Z_TESR_OPAQUE = Tracy.zoneId("tesrOpaque", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_BATCH = Tracy.zoneId("tesrBatch", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_INSTANCED = Tracy.zoneId("tesrInstanced", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_TEXT = Tracy.zoneId("tesrText", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_DEFERRED = Tracy.zoneId("tesrDeferred", Tracy.COLOR_CLIENT);

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
    private final RetainedTesrGroups[] retained = new RetainedTesrGroups[PASS_COUNT];
    final InstancedTemplateRenderer instancedRenderer = new InstancedTemplateRenderer();
    private int activePass = -1;
    private long lastSweepMs;
    private boolean deferredFlushPending;
    private RetainedTesrGroups pendingDeferredHook;

    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();
    private final Vector3f scratchVec = new Vector3f();

    private TesrBatchRenderer() {
        for (int i = 0; i < PASS_COUNT; i++) {
            retained[i] = new RetainedTesrGroups(bufferSource);
        }
    }

    public void beginPass(int passKey, Matrix4f baseMV, double camX, double camY, double camZ) {
        if (deferredFlushPending) {
            discardDeferred();
        }
        activePass = passKey;
        retained[passKey].beginPass(baseMV, camX, camY, camZ, instancedCapable() ? instancedRenderer : null, deferredPipeline());
        if (passKey == PASS_MAIN_0) {
            final long now = System.currentTimeMillis();
            if (now - lastSweepMs >= SWEEP_INTERVAL_MS) {
                lastSweepMs = now;
                for (int i = 0; i < PASS_COUNT; i++) {
                    retained[i].sweep(now);
                }
                instancedRenderer.sweep(now);
            }
        }
    }

    static boolean instancedCapable() {
        if (!ShaderManager.getInstance().isEnabled()) return false;
        if (!Iris.enabled) return true;
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline instanceof FixedFunctionWorldRenderingPipeline) return true;
        return pipeline instanceof DeferredWorldRenderingPipeline deferred && deferred.supportsTesrInstancing();
    }

    static DeferredWorldRenderingPipeline deferredPipeline() {
        if (!Iris.enabled) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline deferred
            ? deferred : null;
    }

    private void discardDeferred() {
        deferredFlushPending = false;
        pendingDeferredHook = null;
        bufferSource.discard();
        BatchingFontRenderer.discardDeferredText();
    }

    public void clearRetained() {
        discardDeferred();
        for (int i = 0; i < PASS_COUNT; i++) {
            retained[i].clear();
        }
        instancedRenderer.clear();
        immediateMesh.delete();
        immediateScratch = null;
        bufferSource.freeBuffers();
        AngelicaTesrMeshCache.INSTANCE.clear();
    }

    public boolean hasActivePass() {
        return activePass >= 0;
    }

    public boolean hasPendingGeometry() {
        return activePass >= 0 || deferredFlushPending;
    }

    public void queue(TemplateBuffer template, ResourceLocation texture, TesrMaterial material) {
        modelView.set(GLStateManager.getModelViewMatrix());
        final int packedLight = currentPackedLight(material);
        final int colorABGR = resolveColor(material);
        if (activePass >= 0) {
            final boolean offset = GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            final PolygonState polygon = GLStateManager.getPolygonState();
            final PassOverride pass = PassOverride.capture();
            final RenderLayer layer = layerFor(texture, material, pass,
                offset ? polygon.getOffsetFactor() : 0.0f, offset ? polygon.getOffsetUnits() : 0.0f);
            final int blockEntityId = pass.isEntityPhase()
                ? Math.max(0, CapturedRenderingState.INSTANCE.getCurrentRenderedEntity())
                : CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
            retained[activePass].queue(template, layer, material, modelView, packedLight, colorABGR, blockEntityId, captureTextureMatrix(), false);
        } else {
            drawImmediate(template, texture, material, packedLight, colorABGR);
        }
    }

    private static Matrix4f captureTextureMatrix() {
        final Matrix4f texMatrix = GLStateManager.getTextures().getTextureUnitMatrix(0);
        return MatrixHelper.isIdentity(texMatrix) ? null : texMatrix;
    }

    static final class LayerKey {
        ResourceLocation texture;
        TesrMaterial.Transparency transparency;
        boolean noCull;
        boolean unlit;
        boolean noDepthWrite;
        boolean depthOnly;
        float cutoutAlpha;
        boolean depthEqual;
        TesrMaterial.SpecialRender special;
        TesrShader shader;
        boolean noPass;
        PassOverride pass;
        float offsetFactor;
        float offsetUnits;

        LayerKey set(ResourceLocation texture, TesrMaterial.Transparency transparency, boolean noCull, boolean unlit, boolean noDepthWrite, boolean depthOnly, float cutoutAlpha, boolean depthEqual, TesrMaterial.SpecialRender special, TesrShader shader, boolean noPass, PassOverride pass, float offsetFactor, float offsetUnits) {
            this.texture = texture;
            this.transparency = transparency;
            this.noCull = noCull;
            this.unlit = unlit;
            this.noDepthWrite = noDepthWrite;
            this.depthOnly = depthOnly;
            this.cutoutAlpha = cutoutAlpha;
            this.depthEqual = depthEqual;
            this.special = special;
            this.shader = shader;
            this.noPass = noPass;
            this.pass = pass;
            this.offsetFactor = offsetFactor;
            this.offsetUnits = offsetUnits;
            return this;
        }

        LayerKey copy() {
            return new LayerKey().set(texture, transparency, noCull, unlit, noDepthWrite, depthOnly, cutoutAlpha, depthEqual, special, shader, noPass, pass, offsetFactor, offsetUnits);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayerKey other)) return false;
            return Objects.equals(texture, other.texture) && transparency == other.transparency
                && noCull == other.noCull && unlit == other.unlit && noDepthWrite == other.noDepthWrite
                && depthOnly == other.depthOnly
                && Float.floatToIntBits(cutoutAlpha) == Float.floatToIntBits(other.cutoutAlpha)
                && depthEqual == other.depthEqual && special == other.special && shader == other.shader && noPass == other.noPass
                && Objects.equals(pass, other.pass)
                && Float.floatToIntBits(offsetFactor) == Float.floatToIntBits(other.offsetFactor)
                && Float.floatToIntBits(offsetUnits) == Float.floatToIntBits(other.offsetUnits);
        }

        @Override
        public int hashCode() {
            int h = Objects.hashCode(texture);
            h = h * 31 + Objects.hashCode(transparency);
            h = h * 31 + (noCull ? 1 : 0);
            h = h * 31 + (unlit ? 1 : 0);
            h = h * 31 + (noDepthWrite ? 1 : 0);
            h = h * 31 + (depthOnly ? 1 : 0);
            h = h * 31 + Float.floatToIntBits(cutoutAlpha);
            h = h * 31 + (depthEqual ? 1 : 0);
            h = h * 31 + Objects.hashCode(special);
            h = h * 31 + System.identityHashCode(shader);
            h = h * 31 + (noPass ? 1 : 0);
            h = h * 31 + Objects.hashCode(pass);
            h = h * 31 + Float.floatToIntBits(offsetFactor);
            h = h * 31 + Float.floatToIntBits(offsetUnits);
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
    private RenderLayer lastLayer;
    private ResourceLocation lastImmediateTexture;
    private TesrMaterial lastImmediateMaterial;
    private RenderLayer lastImmediateLayer;

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits) {
        if (texture == lastLayerTexture && material == lastLayerMaterial && pass.equals(lastLayerPass)
            && offsetFactor == lastLayerOffsetFactor && offsetUnits == lastLayerOffsetUnits) {
            return lastLayer;
        }
        final RenderLayer layer = layerLookup(texture, material, false, pass, offsetFactor, offsetUnits);
        lastLayerTexture = texture;
        lastLayerMaterial = material;
        lastLayerPass = pass;
        lastLayerOffsetFactor = offsetFactor;
        lastLayerOffsetUnits = offsetUnits;
        lastLayer = layer;
        return layer;
    }

    private RenderLayer noPassLayerFor(ResourceLocation texture, TesrMaterial material) {
        if (texture == lastImmediateTexture && material == lastImmediateMaterial) {
            return lastImmediateLayer;
        }
        final RenderLayer layer = layerLookup(texture, material, true, PassOverride.NONE, 0.0f, 0.0f);
        lastImmediateTexture = texture;
        lastImmediateMaterial = material;
        lastImmediateLayer = layer;
        return layer;
    }

    private RenderLayer layerLookup(ResourceLocation texture, TesrMaterial material, boolean noPass, PassOverride pass, float offsetFactor, float offsetUnits) {
        final LayerKey key = scratchKey.set(texture, material.transparency(), material.isNoCull(), material.isUnlit(), material.isNoDepthWrite(), material.isDepthOnly(), material.cutoutAlpha(), material.isDepthEqual(), material.special(), material.shader(), noPass, pass, offsetFactor, offsetUnits);
        RenderLayer layer = layers.get(key);
        if (layer == null) {
            layer = noPass ? RenderLayer.tesrNoPass(texture, material) : RenderLayer.tesr(texture, material, pass, offsetFactor, offsetUnits);
            layers.put(key.copy(), layer);
        }
        return layer;
    }

    private static int currentPackedLight(TesrMaterial material) {
        if (material.hasLightmap()) {
            return GLSMConfig.packBrightness(material.lightmapX(), material.lightmapY());
        }
        return GLSMConfig.packedLastBrightness();
    }

    private static int resolveColor(TesrMaterial material) {
        if (material.hasColor()) {
            return ColorABGR.pack(material.colorRed(), material.colorGreen(), material.colorBlue(), material.colorAlpha());
        }
        final Color4 color = GLStateManager.getColor();
        return ColorABGR.pack(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public void flush() {
        final RetainedTesrGroups hook = activePass >= 0 ? retained[activePass] : null;
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_OPAQUE);
        try {
            bufferSource.endBatchWithType(TransparencyType.OPAQUE, hook);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        if (activePass == PASS_MAIN_0 && deferredPipeline() != null) {
            bufferSource.pauseBatch();
            pendingDeferredHook = hook;
            deferredFlushPending = true;
            activePass = -1;
            return;
        }
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_BATCH);
        try {
            bufferSource.endBatch(hook);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_INSTANCED);
        try {
            instancedRenderer.endFrame();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_TEXT);
        try {
            BatchingFontRenderer.flushDeferredText();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        activePass = -1;
    }

    public void flushAfterDeferred() {
        if (!deferredFlushPending) return;
        deferredFlushPending = false;
        final RetainedTesrGroups hook = pendingDeferredHook;
        pendingDeferredHook = null;
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_DEFERRED);
        try {
            final EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
            final boolean savedDepthMask = GLStateManager.getDepthState().isEnabled();
            final boolean savedDepthTest = GLStateManager.getDepthTest().isEnabled();
            final boolean savedBlend = GLStateManager.getBlendMode().isEnabled();
            final int savedSrcRgb = GLStateManager.getBlendState().getSrcRgb();
            final int savedDstRgb = GLStateManager.getBlendState().getDstRgb();
            final int savedSrcAlpha = GLStateManager.getBlendState().getSrcAlpha();
            final int savedDstAlpha = GLStateManager.getBlendState().getDstAlpha();
            final boolean savedAlphaTest = GLStateManager.getAlphaTest().isEnabled();
            final int savedAlphaFunc = GLStateManager.getAlphaState().getFunction();
            final float savedAlphaRef = GLStateManager.getAlphaState().getReference();

            GLStateManager.glDepthMask(true);
            GLStateManager.enableDepthTest();
            GLStateManager.enableBlend();
            GLStateManager.defaultBlendFunc();
            entityRenderer.enableLightmap(0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            final boolean wrap = Iris.enabled && GbufferPrograms.getCurrentPhase() == WorldRenderingPhase.NONE;
            if (wrap) GbufferPrograms.beginBlockEntities();
            try {
                bufferSource.endBatch(hook);
                instancedRenderer.endFrame();
                AngelicaBufferSource.rebindPass();
                        BatchingFontRenderer.flushDeferredText();
            } finally {
                if (wrap) GbufferPrograms.endBlockEntities();
                entityRenderer.disableLightmap(0);
                GLStateManager.glDepthMask(savedDepthMask);
                if (savedDepthTest) GLStateManager.enableDepthTest(); else GLStateManager.disableDepthTest();
                if (savedBlend) GLStateManager.enableBlend(); else GLStateManager.disableBlend();
                GLStateManager.tryBlendFuncSeparate(savedSrcRgb, savedDstRgb, savedSrcAlpha, savedDstAlpha);
                if (savedAlphaTest) GLStateManager.enableAlphaTest(); else GLStateManager.disableAlphaTest();
                GLStateManager.glAlphaFunc(savedAlphaFunc, savedAlphaRef);
            }
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private long lastRebuilds, lastRetainedDraws, lastStreamed, lastInstInstances;
    private long lastSummaryNanos;

    public String getDebugSummaryLine() {
        long rebuilds = 0, retainedDraws = 0, streamed = 0, instInstances = 0, retainedBytes = 0;
        int groupCount = 0, streamingCount = 0;
        for (int i = 0; i < PASS_COUNT; i++) {
            final RetainedTesrGroups r = retained[i];
            rebuilds += r.rebuilds;
            retainedDraws += r.retainedDraws;
            streamed += r.streamedInstances;
            instInstances += r.instancedInstances;
            retainedBytes += r.retainedBytes();
            groupCount += r.groupCount();
            streamingCount += r.streamingGroupCount();
        }
        final long now = System.nanoTime();
        final long elapsed = now - lastSummaryNanos;
        final String line = String.format("TESR: %d grp (%d strm), reb %d/s, drw %d/s, inst %d/s, stream %d/s, mem %.1fMB",
            groupCount, streamingCount,
            perSecond(rebuilds - lastRebuilds, elapsed),
            perSecond(retainedDraws - lastRetainedDraws, elapsed),
            perSecond(instInstances - lastInstInstances, elapsed),
            perSecond(streamed - lastStreamed, elapsed),
            (retainedBytes + bufferSource.allocatedBytes() + instancedRenderer.meshBytes()) / 1048576.0);
        lastSummaryNanos = now;
        lastRebuilds = rebuilds;
        lastRetainedDraws = retainedDraws;
        lastStreamed = streamed;
        lastInstInstances = instInstances;
        return line;
    }

    private static long perSecond(long delta, long elapsedNanos) {
        return elapsedNanos <= 0 ? 0 : delta * 1_000_000_000L / elapsedNanos;
    }

    public List<String> getDebugDetailStrings() {
        long retainedBytes = 0;
        for (int i = 0; i < PASS_COUNT; i++) {
            retainedBytes += retained[i].retainedBytes();
        }
        final String mem = String.format("TESR mem: ret %.1fMB, seg %.1fMB, tmpl %d/%.1fMB, cache %d",
            retainedBytes / 1048576.0, bufferSource.allocatedBytes() / 1048576.0,
            instancedRenderer.meshCount(), instancedRenderer.meshBytes() / 1048576.0,
            AngelicaTesrMeshCache.INSTANCE.size());
        final String attribution = rebuildAttributionLine();
        return attribution == null ? Arrays.asList(mem) : Arrays.asList(mem, attribution);
    }

    private final ObjectArrayList<RetainedTesrGroups.Group> rebuilders = new ObjectArrayList<>();

    private String rebuildAttributionLine() {
        rebuilders.clear();
        for (int i = 0; i < PASS_COUNT; i++) {
            retained[i].collectRebuilders(rebuilders);
        }
        if (rebuilders.isEmpty()) return null;
        rebuilders.sort((a, b) -> Integer.compare(b.rebuildsWindow, a.rebuildsWindow));
        final StringBuilder sb = new StringBuilder("TESR reb:");
        final int shown = Math.min(3, rebuilders.size());
        for (int i = 0; i < shown; i++) {
            final RetainedTesrGroups.Group group = rebuilders.get(i);
            if (i > 0) sb.append(',');
            sb.append(' ').append(group.rebuildsWindow).append("x ").append(RetainedTesrGroups.attribution(group)).append(' ').append(group.builtVertexCount).append('v');
        }
        if (rebuilders.size() > shown) {
            sb.append(" +").append(rebuilders.size() - shown);
        }
        for (int i = 0, n = rebuilders.size(); i < n; i++) {
            rebuilders.get(i).rebuildsWindow = 0;
        }
        rebuilders.clear();
        return sb.toString();
    }

    public long statRebuilds() { return sumStat(0); }
    public long statRetainedDraws() { return sumStat(1); }
    public long statInstancedDraws() { return sumStat(2); }
    public long statInstancedInstances() { return sumStat(3); }
    public long statStreamedInstances() { return sumStat(4); }
    public long statVolatilePromotions() { return sumStat(5); }

    private long sumStat(int which) {
        long v = 0;
        for (int i = 0; i < PASS_COUNT; i++) {
            final RetainedTesrGroups r = retained[i];
            v += switch (which) {
                case 0 -> r.rebuilds;
                case 1 -> r.retainedDraws;
                case 2 -> r.instancedDraws;
                case 3 -> r.instancedInstances;
                case 4 -> r.streamedInstances;
                default -> r.streamPromotions;
            };
        }
        return v;
    }

    public long statRetainedBytes() {
        long v = 0;
        for (int i = 0; i < PASS_COUNT; i++) {
            v += retained[i].retainedBytes();
        }
        return v;
    }

    public long statBufferSourceBytes() {
        return bufferSource.allocatedBytes();
    }

    private final MeshBuffer immediateMesh = new MeshBuffer();
    private ByteBuffer immediateScratch;

    private void drawImmediate(TemplateBuffer template, ResourceLocation texture, TesrMaterial material, int packedLight, int colorABGR) {
        final int vertexCount = template.vertexCount;
        if (vertexCount == 0) return;
        final VertexFormat format = SegmentedBufferBuilder.FORMAT;
        final int bytes = format.getVertexSize() * vertexCount;
        immediateScratch = MeshBuffer.ensureCapacity(immediateScratch, bytes, false);
        immediateScratch.clear();
        final long addr = memAddress0(immediateScratch);
        final long end = VertexTransform.writeInstance(addr, format, template, identity, scratchVec, colorABGR, packedLight, null);
        immediateScratch.position((int) (end - addr));
        immediateScratch.flip();
        final RenderLayer layer = noPassLayerFor(texture, material);
        GLStateManager.glPushAttrib(AngelicaBufferSource.SAVED_STATE_BITS);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        layer.startDrawing();
        immediateMesh.upload(format, template.drawMode, immediateScratch, vertexCount);
        immediateMesh.render();
        layer.endDrawing();
        GLStateManager.glPopAttrib();
    }
}
