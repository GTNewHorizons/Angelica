package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.gtnewhorizons.angelica.rendering.tesr.EntityShadowReplay.Rejection;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceColumns;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceRange;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

import java.util.Objects;

import static com.gtnewhorizons.angelica.rendering.items.ItemGlint.canResetStencil;

/**
 * Ordered, pooled submissions captured before the main entity pass.
 */
final class EntityRenderCommands {
    private final ObjectArrayList<Command> commands = new ObjectArrayList<>();
    private final Pool<Model> models = new Pool<>(Model::new, Model::clear);
    private final Pool<ModelGlint> modelGlints = new Pool<>(ModelGlint::new, ModelGlint::clear);
    private final Pool<ItemGlint> itemGlints = new Pool<>(ItemGlint::new, ItemGlint::clear);
    private final Pool<Stream> streams = new Pool<>(Stream::new, Stream::clear);
    private final Pool<Overlay> overlays = new Pool<>(Overlay::new, Overlay::clear);
    private final Pool<InstanceRange> ranges = new Pool<>(InstanceRange::new, InstanceRange::clear);
    private final Matrix4f glintPose = new Matrix4f();
    long sharedParts, sharedRanges, streamEntries;
    private int mergeStart;

    int size() {
        return commands.size();
    }

    int split() {
        mergeStart = size();
        return mergeStart;
    }

    Model model(int recordStart, InstanceColumns source, RenderLayer layer, DrawInputs inputs, int entityId, boolean cubesInsideTexture, Matrix4fc modelView, Matrix4fc texMatrix) {
        if (source != null && size() > recordStart && size() > mergeStart && commands.get(size() - 1) instanceof Model previous) {
            if (previous.source == source && previous.sourceVersion == source.version && previous.end == source.size - 1 && previous.shadowLayer == layer && previous.inputs.matches(inputs) && previous.entityId == entityId
                && previous.cubesInsideTexture == cubesInsideTexture && previous.glint == null && (texMatrix == null ? !previous.hasTextureMatrix : previous.hasTextureMatrix && previous.textureMatrix.equals(texMatrix))) {
                previous.end = source.size;
                return null;
            }
        }
        final Model model = models.acquire();
        commands.add(model);
        model.inputs.copyFrom(inputs);
        model.entityId = entityId;
        model.cubesInsideTexture = cubesInsideTexture;
        model.hasTextureMatrix = texMatrix != null;
        if (texMatrix != null) model.textureMatrix.set(texMatrix);
        model.source = source;
        model.shadowLayer = layer;
        if (source == null) {
            model.modelView.set(modelView);
        } else {
            model.sourceVersion = source.version;
            model.start = source.size - 1;
            model.end = source.size;
        }
        return model;
    }

    ModelGlint modelGlint(int start, int end, DrawInputs inputs) {
        for (int i = start; i < end; i++) {
            if (!(commands.get(i) instanceof Model model) || model.cannotAddGlint()) return null;
        }
        final ModelGlint glint = modelGlints.acquire();
        commands.add(glint);
        glint.inputs.copyFrom(inputs);
        glint.base = (Model) commands.get(start);
        Model previous = null;
        for (int i = start; i < end; i++) {
            final Model model = (Model) commands.get(i);
            addGlint(model, glint, true);
            if (previous != null) previous.nextGlintBase = model;
            previous = model;
        }
        return glint;
    }

    ItemGlint itemGlint(Matrix4fc modelView, DrawInputs inputs) {
        if (!(commands.get(size() - 1) instanceof Model base) || base.cannotAddGlint() || base.drawsCubes() || base.sharedRangeChanged()) return null;
        final Matrix4fc pose = base.source != null ? base.source.pose(base.end - 1, glintPose) : base.modelView;
        if (!pose.equals(modelView)) return null;
        final ItemGlint glint = itemGlints.acquire();
        commands.add(glint);
        glint.inputs.copyFrom(inputs);
        glint.base = base;
        addGlint(base, glint, false);
        return glint;
    }

    private void addGlint(Model base, Glint glint, boolean reuseUpload) {
        base.glint = glint;
        base.replayed = ranges.acquire();
        base.replayed.reuseUpload = reuseUpload;
    }

    void stream(Matrix4fc modelView, int[] vertices, int vertexCount, int entityId, DrawInputs inputs) {
        final Stream stream = streams.acquire();
        commands.add(stream);
        stream.inputs.copyFrom(inputs);
        stream.modelView.set(modelView);
        stream.entityId = entityId;
        stream.capture(vertices, vertexCount);
    }

    void overlay(DeferredEntityOverlay.DeferredEntry source) {
        final Overlay overlay = overlays.acquire();
        commands.add(overlay);
        // The shadow overlay queue is recycled before main-view entity
        overlay.captured.copyFrom(source);
    }

    Rejection validate(int start, int end) {
        for (int i = start; i < end; i++) {
            final Rejection reason = commands.get(i).validate();
            if (reason != null) return reason;
        }
        return null;
    }

    void replay(int start, int end) {
        for (int i = start; i < end; i++) {
            final Command command = commands.get(i);
            command.replay();
            if (Tracy.ENABLED) {
                if (command instanceof Model model && model.source != null) {
                    sharedParts += model.end - model.start;
                    sharedRanges++;
                } else if (command instanceof Stream) {
                    streamEntries++;
                }
            }
        }
    }

    void clear() {
        commands.clear();
        models.clear();
        modelGlints.clear();
        itemGlints.clear();
        streams.clear();
        overlays.clear();
        ranges.clear();
        mergeStart = 0;
    }

    void release() {
        clear();
        models.release();
        modelGlints.release();
        itemGlints.release();
        streams.release();
        overlays.release();
        ranges.release();
    }

    abstract static class Command {
        Rejection validate() {
            return null;
        }

        abstract void replay();
    }

    static final class DrawInputs {
        ResourceLocation texture;
        TesrMaterial material;
        PassOverride pass;
        int textureId, glintSlot, cull;
        float offsetFactor, offsetUnits;
        boolean offsetEnabled, lit, lightmap, afterGlint;
        boolean inheritedOffsetEnable, inheritedOffsetValues, inheritedLighting;

        DrawInputs set(ResourceLocation texture, int textureId, TesrMaterial material, PassOverride pass, boolean offsetEnabled, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit, boolean lightmap, boolean afterGlint) {
            this.texture = texture;
            this.textureId = textureId;
            this.material = material;
            this.pass = pass;
            this.offsetEnabled = offsetEnabled;
            this.offsetFactor = offsetFactor;
            this.offsetUnits = offsetUnits;
            this.glintSlot = glintSlot;
            this.cull = cull;
            this.lit = lit;
            this.lightmap = lightmap;
            this.afterGlint = afterGlint;
            return this;
        }

        private boolean offsetValuesUsed() {
            return !inheritedOffsetValues && (offsetEnabled || inheritedOffsetEnable);
        }

        boolean matches(DrawInputs other) {
            return textureId == other.textureId && Objects.equals(texture, other.texture) && material == other.material && pass == other.pass && (!offsetValuesUsed() || Float.floatToIntBits(offsetFactor)
                == Float.floatToIntBits(other.offsetFactor) && Float.floatToIntBits(offsetUnits) == Float.floatToIntBits(other.offsetUnits)) && offsetEnabled == other.offsetEnabled && glintSlot == other.glintSlot && cull == other.cull
                && lit == other.lit && lightmap == other.lightmap && afterGlint == other.afterGlint && inheritedOffsetEnable == other.inheritedOffsetEnable && inheritedOffsetValues == other.inheritedOffsetValues && inheritedLighting
                == other.inheritedLighting;
        }

        void copyFrom(DrawInputs other) {
            set(other.texture, other.textureId, other.material, other.pass, other.offsetEnabled, other.offsetFactor, other.offsetUnits, other.glintSlot, other.cull, other.lit, other.lightmap, other.afterGlint);
            inheritedOffsetEnable = other.inheritedOffsetEnable;
            inheritedOffsetValues = other.inheritedOffsetValues;
            inheritedLighting = other.inheritedLighting;
        }

        void clear() {
            texture = null;
            material = null;
            pass = null;
        }
    }

    static final class Instance {
        TemplateBuffer template;
        int light, color, overlay;
        long entityInfo;

        void set(TemplateBuffer template, int light, int color, int overlay, long entityInfo) {
            this.template = template;
            this.light = light;
            this.color = color;
            this.overlay = overlay;
            this.entityInfo = entityInfo;
        }
    }

    abstract static class Draw extends Command {
        final DrawInputs inputs = new DrawInputs();
        int entityId;

        void clear() {
            inputs.clear();
        }
    }

    static final class Model extends Draw {
        final Matrix4f modelView = new Matrix4f();
        final Matrix4f textureMatrix = new Matrix4f();
        final Instance instance = new Instance();
        InstanceColumns source;
        long sourceVersion;
        int start, end;
        RenderLayer shadowLayer;
        CubeParams[] cubes;
        boolean cubesInsideTexture, hasTextureMatrix;
        float scale;
        Glint glint;
        Model nextGlintBase;
        InstanceRange replayed;

        boolean cannotAddGlint() {
            final TesrMaterial material = inputs.material;
            return material == null || !material.isStream() || material.shader() != null || glint != null;
        }

        boolean drawsCubes() {
            return source != null ? !source.cubes.isEmpty() : cubes != null;
        }

        boolean sharedRangeChanged() {
            return source != null && (source.version != sourceVersion || end > source.size);
        }

        @Override
        Rejection validate() {
            if (sharedRangeChanged()) return Rejection.SHARED_RANGE_CHANGED;
            if (glint != null && !ModelPartBatcher.INSTANCE.canInstanceBaseAndGlint(this, glint.inputs.lightmap)) return Rejection.NOT_READY;
            return null;
        }

        @Override
        void replay() {
            ModelPartBatcher.INSTANCE.replay(this);
        }

        @Override
        void clear() {
            super.clear();
            source = null;
            shadowLayer = null;
            instance.template = null;
            cubes = null;
            glint = null;
            nextGlintBase = null;
            replayed = null;
        }
    }

    abstract static class Glint extends Draw {
        Model base;

        @Override
        Rejection validate() {
            if (!ModelPartBatcher.INSTANCE.canReplayGlint(inputs.material)) return Rejection.NOT_READY;
            return inputs.material == EntityMaterials.ITEM_GLINT && !canResetStencil() ? Rejection.UNCAPTURED_STATE : null;
        }

        @Override
        void clear() {
            super.clear();
            base = null;
        }
    }

    static final class ModelGlint extends Glint {
        @Override
        void replay() {
            ModelPartBatcher.INSTANCE.replay(this);
        }
    }

    static final class ItemGlint extends Glint {
        final Instance instance = new Instance();

        @Override
        void replay() {
            ModelPartBatcher.INSTANCE.replay(this);
        }

        @Override
        void clear() {
            super.clear();
            instance.template = null;
        }
    }

    static final class Stream extends Draw {
        final Matrix4f modelView = new Matrix4f();
        int[] vertices;
        int vertexCount, entity, blockEntity, item;
        float red, green, blue, alpha;

        void capture(int[] vertices, int vertexCount) {
            final int length = vertexCount * ModelQuadUtil.VERTEX_SIZE;
            if (this.vertices == null) {
                this.vertices = new int[length];
            } else if (this.vertices.length < length) {
                this.vertices = new int[Math.max(length, this.vertices.length << 1)];
            }
            System.arraycopy(vertices, 0, this.vertices, 0, length);
            this.vertexCount = vertexCount;
            final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
            entity = state.getCurrentRenderedEntity();
            blockEntity = state.getCurrentRenderedBlockEntity();
            item = state.getCurrentRenderedItem();
            final Vector4fc color = state.getCurrentEntityColor();
            red = color.x();
            green = color.y();
            blue = color.z();
            alpha = color.w();
        }

        @Override
        void replay() {
            ModelPartBatcher.INSTANCE.replay(this);
        }
    }

    private static final class Overlay extends Command {
        final DeferredEntityOverlay.DeferredEntry captured = new DeferredEntityOverlay.DeferredEntry();

        @Override
        void replay() {
            DeferredEntityOverlay.queueReplay(captured, ModelPartBatcher.INSTANCE.replayTransform);
        }

        void clear() {
            captured.clear();
        }
    }
}
