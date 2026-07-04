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
import com.gtnewhorizons.angelica.glsm.states.Color4;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
    private final RetainedTesrGroups[] retained = new RetainedTesrGroups[PASS_COUNT];
    final InstancedTemplateRenderer instancedRenderer = new InstancedTemplateRenderer();
    private int activePass = -1;
    private long lastSweepMs;

    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();
    private final Vector3f scratchVec = new Vector3f();

    private TesrBatchRenderer() {
        for (int i = 0; i < PASS_COUNT; i++) {
            retained[i] = new RetainedTesrGroups(bufferSource);
        }
    }

    public void beginPass(int passKey, Matrix4f baseMV, double camX, double camY, double camZ) {
        activePass = passKey;
        retained[passKey].beginPass(baseMV, camX, camY, camZ, instancedCapable() ? instancedRenderer : null);
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
        return pipeline == null || pipeline instanceof FixedFunctionWorldRenderingPipeline;
    }

    public void clearRetained() {
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

    public void queue(TemplateBuffer template, ResourceLocation texture, TesrMaterial material) {
        modelView.set(GLStateManager.getModelViewMatrix());
        final int packedLight = currentPackedLight(material);
        final int colorABGR = resolveColor(material);
        if (activePass >= 0) {
            final RenderLayer layer = layerFor(texture, material);
            final int blockEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
            retained[activePass].queue(template, layer, material, modelView, packedLight, colorABGR, blockEntityId, captureTextureMatrix());
        } else {
            drawImmediate(template, texture, material, packedLight, colorABGR);
        }
    }

    private static Matrix4f captureTextureMatrix() {
        final Matrix4f texMatrix = GLStateManager.getTextures().getTextureUnitMatrix(0);
        return MatrixHelper.isIdentity(texMatrix) ? null : new Matrix4f(texMatrix);
    }

    static final class LayerKey {
        ResourceLocation texture;
        TesrMaterial.Transparency transparency;
        boolean noCull;
        boolean unlit;
        boolean noDepthWrite;
        float cutoutAlpha;
        boolean depthEqual;
        TesrShader shader;
        boolean noPass;

        LayerKey set(ResourceLocation texture, TesrMaterial.Transparency transparency, boolean noCull, boolean unlit, boolean noDepthWrite, float cutoutAlpha, boolean depthEqual, TesrShader shader, boolean noPass) {
            this.texture = texture;
            this.transparency = transparency;
            this.noCull = noCull;
            this.unlit = unlit;
            this.noDepthWrite = noDepthWrite;
            this.cutoutAlpha = cutoutAlpha;
            this.depthEqual = depthEqual;
            this.shader = shader;
            this.noPass = noPass;
            return this;
        }

        LayerKey copy() {
            return new LayerKey().set(texture, transparency, noCull, unlit, noDepthWrite, cutoutAlpha, depthEqual, shader, noPass);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayerKey other)) return false;
            return Objects.equals(texture, other.texture) && transparency == other.transparency
                && noCull == other.noCull && unlit == other.unlit && noDepthWrite == other.noDepthWrite
                && Float.floatToIntBits(cutoutAlpha) == Float.floatToIntBits(other.cutoutAlpha)
                && depthEqual == other.depthEqual && shader == other.shader && noPass == other.noPass;
        }

        @Override
        public int hashCode() {
            int h = Objects.hashCode(texture);
            h = h * 31 + Objects.hashCode(transparency);
            h = h * 31 + (noCull ? 1 : 0);
            h = h * 31 + (unlit ? 1 : 0);
            h = h * 31 + (noDepthWrite ? 1 : 0);
            h = h * 31 + Float.floatToIntBits(cutoutAlpha);
            h = h * 31 + (depthEqual ? 1 : 0);
            h = h * 31 + System.identityHashCode(shader);
            h = h * 31 + (noPass ? 1 : 0);
            return h;
        }
    }

    private final Object2ObjectOpenHashMap<LayerKey, RenderLayer> layers = new Object2ObjectOpenHashMap<>();
    private final LayerKey scratchKey = new LayerKey();
    private ResourceLocation lastLayerTexture;
    private TesrMaterial lastLayerMaterial;
    private RenderLayer lastLayer;
    private ResourceLocation lastImmediateTexture;
    private TesrMaterial lastImmediateMaterial;
    private RenderLayer lastImmediateLayer;

    private RenderLayer layerFor(ResourceLocation texture, TesrMaterial material) {
        if (texture == lastLayerTexture && material == lastLayerMaterial) {
            return lastLayer;
        }
        final RenderLayer layer = layerLookup(texture, material, false);
        lastLayerTexture = texture;
        lastLayerMaterial = material;
        lastLayer = layer;
        return layer;
    }

    private RenderLayer noPassLayerFor(ResourceLocation texture, TesrMaterial material) {
        if (texture == lastImmediateTexture && material == lastImmediateMaterial) {
            return lastImmediateLayer;
        }
        final RenderLayer layer = layerLookup(texture, material, true);
        lastImmediateTexture = texture;
        lastImmediateMaterial = material;
        lastImmediateLayer = layer;
        return layer;
    }

    private RenderLayer layerLookup(ResourceLocation texture, TesrMaterial material, boolean noPass) {
        final LayerKey key = scratchKey.set(texture, material.transparency(), material.isNoCull(), material.isUnlit(), material.isNoDepthWrite(), material.cutoutAlpha(), material.isDepthEqual(), material.shader(), noPass);
        RenderLayer layer = layers.get(key);
        if (layer == null) {
            layer = noPass ? RenderLayer.tesrNoPass(texture, material) : RenderLayer.tesr(texture, material);
            layers.put(key.copy(), layer);
        }
        return layer;
    }

    private static int currentPackedLight(TesrMaterial material) {
        if (material.hasLightmap()) {
            return ((int) material.lightmapY() << 16) | ((int) material.lightmapX() & 0xFFFF);
        }
        return ((int) GLSMConfig.lastBrightnessY << 16) | ((int) GLSMConfig.lastBrightnessX & 0xFFFF);
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
        bufferSource.endBatchWithType(TransparencyType.OPAQUE, hook);
        bufferSource.endBatch(hook);
        instancedRenderer.endFrame();
        BatchingFontRenderer.flushDeferredText();
        activePass = -1;
    }

    private long lastRebuilds, lastRetainedDraws, lastPromotions, lastStreamed, lastInstDraws, lastInstInstances;

    public List<String> getDebugStrings() {
        long rebuilds = 0, retainedDraws = 0, promotions = 0, streamed = 0, retainedBytes = 0, instDraws = 0, instInstances = 0;
        int groupCount = 0, streamingCount = 0;
        for (int i = 0; i < PASS_COUNT; i++) {
            final RetainedTesrGroups r = retained[i];
            rebuilds += r.rebuilds;
            retainedDraws += r.retainedDraws;
            promotions += r.streamPromotions;
            streamed += r.streamedInstances;
            instDraws += r.instancedDraws;
            instInstances += r.instancedInstances;
            retainedBytes += r.retainedBytes();
            groupCount += r.groupCount();
            streamingCount += r.streamingGroupCount();
        }
        final String line1 = String.format("TESR: %d grp (%d strm), reb %d, drw %d, stream %d, inst %d/%d, promo %d",
            groupCount, streamingCount,
            rebuilds - lastRebuilds, retainedDraws - lastRetainedDraws,
            streamed - lastStreamed,
            instInstances - lastInstInstances, instDraws - lastInstDraws,
            promotions - lastPromotions);
        lastRebuilds = rebuilds;
        lastRetainedDraws = retainedDraws;
        lastPromotions = promotions;
        lastStreamed = streamed;
        lastInstDraws = instDraws;
        lastInstInstances = instInstances;
        final String line2 = String.format("TESR mem: ret %.1fMB, seg %.1fMB, tmpl %d/%.1fMB, cache %d",
            retainedBytes / 1048576.0, bufferSource.allocatedBytes() / 1048576.0,
            instancedRenderer.meshCount(), instancedRenderer.meshBytes() / 1048576.0,
            AngelicaTesrMeshCache.INSTANCE.size());
        return Arrays.asList(line1, line2);
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
