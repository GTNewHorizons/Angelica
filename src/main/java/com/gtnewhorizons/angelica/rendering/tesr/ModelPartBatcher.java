package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

public final class ModelPartBatcher {

    public static final ModelPartBatcher INSTANCE = new ModelPartBatcher();

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final AngelicaBufferSource bufferSource = new AngelicaBufferSource();
    private final RetainedTesrGroups groups = new RetainedTesrGroups(bufferSource);
    private final AngelicaTesrMeshCache.GtnhMeshBackend captureBackend = new AngelicaTesrMeshCache.GtnhMeshBackend();
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private boolean active;
    private long lastSweepMs;

    long parts;
    long liveFallbacks;
    private long lastParts, lastLiveFallbacks;

    private static final class PartTemplate {
        TemplateBuffer template;
        float scale;
        long lastUsedMs;
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

    public void begin() {
        if (!AngelicaConfig.enableEntityBatching || !TesrBatchRenderer.instancedCapable()) {
            active = false;
            return;
        }
        active = true;
        groups.beginPass(identity, 0, 0, 0, TesrBatchRenderer.INSTANCE.instancedRenderer);
        final long now = System.currentTimeMillis();
        if (now - lastSweepMs >= SWEEP_INTERVAL_MS) {
            lastSweepMs = now;
            groups.sweep(now);
            sweepTemplates(now);
        }
    }

    public void flush() {
        if (!active) return;
        active = false;
        bufferSource.endBatchWithType(TransparencyType.OPAQUE, groups);
        bufferSource.endBatch(groups);
        TesrBatchRenderer.INSTANCE.instancedRenderer.endFrame();
    }

    public boolean isActive() {
        return active;
    }

    public static boolean partDraw(ModelRenderer part, float scale) {
        final ModelPartBatcher r = INSTANCE;
        if (!r.active || DisplayListManager.isRecording()) return false;
        if (!r.queuePart(part, scale)) {
            r.liveFallbacks++;
            return false;
        }
        return true;
    }

    private boolean queuePart(ModelRenderer part, float scale) {
        final TesrMaterial material = EntityMaterials.fromCurrentState();
        if (material == null) return false;
        final ResourceLocation texture;
        if (material == EntityMaterials.OVERLAY) {
            texture = null;
        } else {
            if (GLStateManager.getBoundTextureForServerState() != lastBoundGlId) return false;
            texture = lastBoundLocation;
            if (texture == null) return false;
        }
        final TemplateBuffer template = templateFor(part, scale);
        if (template == null) return false;

        final RenderLayer layer = layerFor(texture, material);
        final int entityId = Math.max(0, CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
        final Color4 color = GLStateManager.getColor();
        final int colorABGR = ColorABGR.pack(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        final int packedLight = ((int) GLSMConfig.lastBrightnessY << 16) | ((int) GLSMConfig.lastBrightnessX & 0xFFFF);
        modelView.set(GLStateManager.getModelViewMatrix());
        groups.queue(template, layer, material, modelView, packedLight, colorABGR, entityId, null);
        parts++;
        return true;
    }

    private TemplateBuffer templateFor(ModelRenderer part, float scale) {
        PartTemplate entry = templates.get(part);
        if (entry == null || entry.scale != scale) {
            entry = capture(part, scale, entry);
        }
        entry.lastUsedMs = lastSweepMs;
        return entry.template;
    }

    private PartTemplate capture(ModelRenderer part, float scale, PartTemplate reuse) {
        final PartTemplate entry = reuse != null ? reuse : new PartTemplate();
        entry.scale = scale;
        entry.template = null;
        final List<ModelBox> boxes = part.cubeList;
        if (boxes != null && !boxes.isEmpty()) {
            final Tessellator tess = captureBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
            for (int i = 0, n = boxes.size(); i < n; i++) {
                boxes.get(i).render(tess, scale);
            }
            entry.template = captureBackend.endCaptureToTemplate();
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
        templates.clear();
        bufferSource.freeBuffers();
        active = false;
    }

    public String getDebugString() {
        final String line = String.format("Parts: %d grp, inst %d, live %d", groups.groupCount(), parts - lastParts, liveFallbacks - lastLiveFallbacks);
        lastParts = parts;
        lastLiveFallbacks = liveFallbacks;
        return line;
    }
}
