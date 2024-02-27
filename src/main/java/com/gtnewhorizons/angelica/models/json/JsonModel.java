package com.gtnewhorizons.angelica.models.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.Axis;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import com.gtnewhorizons.angelica.utils.DirUtil;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.gtnewhorizons.angelica.utils.JsonUtil.*;
import static me.jellysquid.mods.sodium.common.util.DirectionUtil.ALL_DIRECTIONS;

public class JsonModel implements QuadProvider {

    @Nullable
    private final ResourceLocation parentId;
    @Nullable
    private JsonModel parent;
    @Getter
    private final boolean useAO;
    private final Map<ModelDisplay.Position, ModelDisplay> display;
    private final Map<String, String> textures;
    private List<ModelElement> elements;
    private List<Quad> allQuadStore = new ObjectArrayList<>();
    private final Map<ForgeDirection, List<Quad>> sidedQuadStore = new Object2ObjectOpenHashMap<>();
    private static final List<Quad> EMPTY = ObjectImmutableList.of();

    JsonModel(@Nullable ResourceLocation parentId, boolean useAO, Map<ModelDisplay.Position, ModelDisplay> display, Map<String, String> textures, List<ModelElement> elements) {
        this.parentId = parentId;
        this.useAO = useAO;
        this.display = display;
        this.textures = textures;
        this.elements = elements;
    }

    /**
     * Makes a shallow copy of og. This allows you to bake the same model multiple times with various transformations.
     */
    JsonModel(JsonModel og) {

        this.parentId = og.parentId;
        this.parent = og.parent;
        this.useAO = og.useAO;
        this.display = og.display;
        this.textures = og.textures;
        this.elements = og.elements;
    }

    public void bake(Variant v) {

        final Matrix4f vRot = v.getAffineMatrix();

        final NdQuadBuilder builder = new NdQuadBuilder();

        // Append faces from each element
        for (ModelElement e : this.elements) {

            final Matrix4f rot = (e.getRotation() == null)
                ? ModelElement.Rotation.NOOP.getAffineMatrix()
                : e.getRotation().getAffineMatrix();

            final Vector3f from = e.getFrom();
            final Vector3f to = e.getTo();

            for (ModelElement.Face f : e.getFaces()) {

                // Assign vertexes
                for (int i = 0; i < 4; ++i) {

                    final Vector3f vert =
                        rot.transformPosition(NdQuadBuilder.mapSideToVertex(from, to, i, f.getName()));
                    vRot.transformPosition(vert);
                    builder.pos(i, vert.x, vert.y, vert.z);
                }

                // Set culling and nominal faces
                builder.cullFace(f.getCullFace());
                builder.nominalFace(f.getName());

                // Set bake flags
                int flags = switch(f.getRotation()) {
                    case 90 -> NdQuadBuilder.BAKE_ROTATE_90;
                    case 180 -> NdQuadBuilder.BAKE_ROTATE_180;
                    case 270 -> NdQuadBuilder.BAKE_ROTATE_270;
                    default -> NdQuadBuilder.BAKE_ROTATE_NONE;
                };

                // Set UV
                final Vector4f uv = f.getUv();
                if (uv != null) {

                    builder.uv(0, uv.x, uv.y);
                    builder.uv(1, uv.x, uv.w);
                    builder.uv(2, uv.z, uv.w);
                    builder.uv(3, uv.z, uv.y);
                } else {

                    // Not sure if this is correct, but it seems to fix things
                    flags |= NdQuadBuilder.BAKE_LOCK_UV;
                }

                // Set the sprite
                builder.spriteBake(this.textures.get(f.getTexture()), flags);

                // Set the tint index
                final int tint = f.getTintIndex();
                builder.color(tint, tint, tint, tint);

                // Set AO
                builder.mat.setAO(this.useAO);

                // Bake and add it
                final Quad q = builder.build(new Quad());
                this.allQuadStore.add(q);
                this.sidedQuadStore.computeIfAbsent(f.getCullFace(),
                    o -> new ObjectArrayList<>()).add(q);
            }
        }

        // Lock the lists.
        this.allQuadStore = new ObjectImmutableList<>(this.allQuadStore);
        for (ForgeDirection f : ALL_DIRECTIONS) {

            List<Quad> l = this.sidedQuadStore.computeIfAbsent(f, o -> EMPTY);
            if (!l.isEmpty())
                this.sidedQuadStore.put(f, new ObjectImmutableList<>(l));
        }
    }

    public List<ResourceLocation> getParents() {
        return Arrays.asList(parentId);
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, ObjectPooler<Quad> quadPool) {

        final List<Quad> src = this.sidedQuadStore.getOrDefault(dir, EMPTY);
        final List<Quad> ret = new ObjectArrayList<>(src.size());

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < src.size(); ++i) {
            final Quad q = quadPool.getInstance();
            q.copyFrom(src.get(i));
            ret.add(q);
        }

        return ret;
    }

    public void resolveParents(Function<ResourceLocation, JsonModel> modelLoader) {

        if (this.parentId != null && this.parent == null) {

            final JsonModel p = modelLoader.apply(this.parentId);
            p.resolveParents(modelLoader);

            // Inherit properties
            this.parent = p;
            if (this.elements.isEmpty()) this.elements = this.parent.elements;

            // Resolve texture variables
            // Add parent texture mappings, but prioritize ours.
            for (Map.Entry<String, String> e : this.parent.textures.entrySet()) {

                this.textures.putIfAbsent(e.getKey(), e.getValue());
            }

            // Flatten them, merging s -> s1, s1 -> s2 to s -> s2, s1 -> s2.
            boolean flat = false;
            final Map<String, String> tmp = new Object2ObjectOpenHashMap<>();
            while (!flat) {
                flat = true;

                for (Map.Entry<String, String> e : this.textures.entrySet()) {

                    // If there is a value in the key set, replace with the value it points to
                    // Also avoid adding a loop
                    if (this.textures.containsKey(e.getValue())) {

                        if (!e.getKey().equals(e.getValue()))
                            tmp.put(e.getKey(), this.textures.get(e.getValue()));
                        else tmp.put(e.getKey(), "");
                        flat = false;
                    } else {
                        tmp.put(e.getKey(), e.getValue());
                    }
                }
                this.textures.putAll(tmp);
            }
        }
    }

    static class Deserializer implements JsonDeserializer<JsonModel> {

        private Vector3f loadVec3(JsonObject in, String name) {

            final JsonArray arr = in.getAsJsonArray(name);
            final Vector3f ret = new Vector3f();

            for (int i = 0; i < 3; ++i) {
                ret.setComponent(i, arr.get(i).getAsFloat());
            }

            return ret;
        }

        private Vector4f loadVec4(JsonObject in, String name) {

            final JsonArray arr = in.getAsJsonArray(name);
            final Vector4f ret = new Vector4f();

            for (int i = 0; i < 4; ++i) {
                ret.setComponent(i, arr.get(i).getAsFloat());
            }

            return ret;
        }

        private ModelDisplay loadADisplay(JsonObject in) {

            final Vector3f rotation = loadVec3(in, "rotation");
            final Vector3f translation = loadVec3(in, "translation");
            final Vector3f scale = loadVec3(in, "scale");

            return new ModelDisplay(rotation, translation, scale);
        }

        private Map<ModelDisplay.Position, ModelDisplay> loadDisplay(JsonObject in) {

            // wow such long
            final Map<ModelDisplay.Position, ModelDisplay> ret = new Object2ObjectOpenHashMap<>(ModelDisplay.Position.values().length);

            if (in.has("display")) {

                final JsonObject display = in.getAsJsonObject("display");

                for (Map.Entry<String, JsonElement> j : display.entrySet()) {

                    final String name = j.getKey();
                    final ModelDisplay.Position pos = ModelDisplay.Position.getByName(name);
                    ret.put(pos, loadADisplay(j.getValue().getAsJsonObject()));
                }
            }

            for (ModelDisplay.Position p : ModelDisplay.Position.values()) {
                ret.putIfAbsent(p, ModelDisplay.DEFAULT);
            }

            return ret;
        }

        private Map<String, String> loadTextures(JsonObject in) {

            final Map<String, String> textures = new Object2ObjectOpenHashMap<>();

            if (in.has("textures")) {
                for (Map.Entry<String, JsonElement> e : in.getAsJsonObject("textures").entrySet()) {

                    // Trim leading octothorpes. They indicate a texture variable, but I don't actually care.
                    String s = e.getValue().getAsString();
                    if (s.startsWith("#")) s = s.substring(1);
                    textures.put(e.getKey(), s);
                }
            }

            return textures;
        }

        private ModelElement.Rotation loadRotation(JsonObject in) {

            if (in.has("rotation")) {
                final JsonObject json = in.getAsJsonObject("rotation");

                final Vector3f origin = loadVec3(json, "origin").div(16);
                final Axis axis = Axis.fromName(loadStr(json, "axis"));
                final float angle = loadFloat(json, "angle");
                final boolean rescale = loadBool(json, "rescale", false);

                return new ModelElement.Rotation(origin, axis, angle, rescale);
            } else {

                return null;
            }
        }

        private List<ModelElement.Face> loadFaces(JsonObject in) {

            final List<ModelElement.Face> ret = new ObjectArrayList<>();
            final JsonObject json = in.getAsJsonObject("faces");

            for (Map.Entry<String, JsonElement> e : json.entrySet()) {

                final ForgeDirection side = DirUtil.fromName(e.getKey());
                final JsonObject face = e.getValue().getAsJsonObject();

                final Vector4f uv = (face.has("uv")) ? loadVec4(face, "uv") : null;
                String texture = loadStr(face, "texture");
                if (texture.startsWith("#")) texture = texture.substring(1);
                final ForgeDirection cullFace = DirUtil.fromName(loadStr(face, "cullface", "unknown"));
                final int rotation = loadInt(face, "rotation", 0);
                final int tintIndex = loadInt(face, "tintindex", -1);

                ret.add(new ModelElement.Face(side, uv, texture, cullFace, rotation, tintIndex));
            }

            return ret;
        }

        private List<ModelElement> loadElements(JsonObject in) {

            final List<ModelElement> ret = new ObjectArrayList<>();

            if (in.has("elements")) {

                final JsonArray arr = in.getAsJsonArray("elements");
                for (JsonElement e : arr) {

                    final JsonObject json = e.getAsJsonObject();
                    final Vector3f from = loadVec3(json, "from").div(16);
                    final Vector3f to = loadVec3(json, "to").div(16);
                    final ModelElement.Rotation rotation = loadRotation(json);
                    final boolean shade = loadBool(json, "shade", true);
                    final List<ModelElement.Face> faces = loadFaces(json);

                    ret.add(new ModelElement(from, to, rotation, shade, faces));
                }
            }

            return ret;
        }

        @Override
        public JsonModel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

            final JsonObject in = json.getAsJsonObject();

            final String parent = loadStr(in, "parent", "");
            final ResourceLocation parentId = (parent.isEmpty()) ? null : new ResourceLocation(parent);

            final boolean useAO = loadBool(in, "ambientocclusion", true);
            final Map<ModelDisplay.Position, ModelDisplay> display = loadDisplay(in);
            final Map<String, String> textures = loadTextures(in);
            final List<ModelElement> elements = loadElements(in);

            return new JsonModel(parentId, useAO, display, textures, elements);
        }
    }
}
