package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Cached-mesh entry points for the vanilla chest/sign/skull TESR mixins. */
public final class VanillaModelMeshes {

    private static final float MODEL_SCALE = 0.0625f;
    private static final float LID_CLOSED_EPS = 1.0e-4f;
    private static final TesrMaterial WHITE = TesrMaterial.builder().color(1f, 1f, 1f, 1f).build();
    private static final ResourceLocation SIGN_TEXTURE = new ResourceLocation("textures/entity/sign.png");

    private static final class ChestKeys {
        final Object closed = new Object();
        final Object base = new Object();
        final Object lid = new Object();
        final Object knob = new Object();
    }

    private static final Reference2ObjectOpenHashMap<ResourceLocation, ChestKeys> SINGLE_CHEST_KEYS = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<ResourceLocation, ChestKeys> DOUBLE_CHEST_KEYS = new Reference2ObjectOpenHashMap<>();
    private static final Object KEY_SIGN_STANDING = new Object();
    private static final Object KEY_SIGN_WALL = new Object();

    private record SkullKey(ModelSkeletonHead model, ResourceLocation texture) {}

    private static final Map<SkullKey, Object> SKULL_KEYS = new HashMap<>();

    private static final Builder BUILDER = new Builder();

    private VanillaModelMeshes() {}

    public static void renderChest(ModelChest model, ResourceLocation texture, boolean isDouble) {
        final float lidAngle = model.chestLid.rotateAngleX;
        final ChestKeys keys = chestKeys(texture, isDouble);
        if (Math.abs(lidAngle) <= LID_CLOSED_EPS) {
            BUILDER.set(texture, MODEL_SCALE, model.chestLid, model.chestKnob, model.chestBelow);
            AngelicaTesrMeshCache.INSTANCE.renderCached(keys.closed, BUILDER);
            return;
        }
        BUILDER.set(texture, MODEL_SCALE, model.chestBelow, null, null);
        AngelicaTesrMeshCache.INSTANCE.renderCached(keys.base, BUILDER);
        renderRotatedPart(keys.lid, texture, model.chestLid, lidAngle);
        renderRotatedPart(keys.knob, texture, model.chestKnob, lidAngle);
    }

    private static ChestKeys chestKeys(ResourceLocation texture, boolean isDouble) {
        final Reference2ObjectOpenHashMap<ResourceLocation, ChestKeys> keys = isDouble ? DOUBLE_CHEST_KEYS : SINGLE_CHEST_KEYS;
        ChestKeys k = keys.get(texture);
        if (k == null) {
            k = new ChestKeys();
            keys.put(texture, k);
        }
        return k;
    }

    private static void renderRotatedPart(Object key, ResourceLocation texture, ModelRenderer part, float angleX) {
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(part.offsetX + part.rotationPointX * MODEL_SCALE, part.offsetY + part.rotationPointY * MODEL_SCALE, part.offsetZ + part.rotationPointZ * MODEL_SCALE);
        GLStateManager.glRotatef(angleX * (180f / (float) Math.PI), 1.0f, 0.0f, 0.0f);
        BUILDER.setLocal(texture, MODEL_SCALE, part);
        AngelicaTesrMeshCache.INSTANCE.renderCached(key, BUILDER);
        GLStateManager.glPopMatrix();
    }

    public static void renderSign(ModelSign model) {
        final boolean standing = model.signStick.showModel;
        BUILDER.set(SIGN_TEXTURE, MODEL_SCALE, model.signBoard, standing ? model.signStick : null, null);
        AngelicaTesrMeshCache.INSTANCE.renderCached(standing ? KEY_SIGN_STANDING : KEY_SIGN_WALL, BUILDER);
    }

    public static boolean renderSkull(ModelSkeletonHead model, ResourceLocation texture, float yawDegrees, float pitchDegrees, float scale) {
        if (texture == null) {
            return false;
        }
        final Object key = SKULL_KEYS.computeIfAbsent(new SkullKey(model, texture), k -> new Object());
        GLStateManager.glPushMatrix();
        if (yawDegrees != 0.0f) {
            GLStateManager.glRotatef(yawDegrees, 0.0f, 1.0f, 0.0f);
        }
        if (pitchDegrees != 0.0f) {
            GLStateManager.glRotatef(pitchDegrees, 1.0f, 0.0f, 0.0f);
        }
        BUILDER.set(texture, scale, model.skeletonHead, null, null);
        AngelicaTesrMeshCache.INSTANCE.renderCached(key, BUILDER);
        GLStateManager.glPopMatrix();
        return true;
    }

    private static final class Builder implements TesrMeshBuilder {
        private ResourceLocation texture;
        private float scale;
        private ModelRenderer part0, part1, part2;
        private boolean partLocal;

        void set(ResourceLocation texture, float scale, ModelRenderer part0, ModelRenderer part1, ModelRenderer part2) {
            this.texture = texture;
            this.scale = scale;
            this.part0 = part0;
            this.part1 = part1;
            this.part2 = part2;
            this.partLocal = false;
        }

        void setLocal(ResourceLocation texture, float scale, ModelRenderer part) {
            set(texture, scale, part, null, null);
            this.partLocal = true;
        }

        @Override
        public void angelica$build(TesrMeshSink sink) {
            final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, texture, WHITE);
            if (partLocal) {
                ModelPartMesher.emitPartLocal(t, part0, scale);
                return;
            }
            ModelPartMesher.emitPart(t, part0, scale);
            if (part1 != null) ModelPartMesher.emitPart(t, part1, scale);
            if (part2 != null) ModelPartMesher.emitPart(t, part2, scale);
        }
    }
}
