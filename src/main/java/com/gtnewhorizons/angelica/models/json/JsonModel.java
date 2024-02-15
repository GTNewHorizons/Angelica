package com.gtnewhorizons.angelica.models.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.gtnewhorizons.angelica.utils.JsonUtil.loadBool;
import static com.gtnewhorizons.angelica.utils.JsonUtil.loadFloat;
import static com.gtnewhorizons.angelica.utils.JsonUtil.loadInt;
import static com.gtnewhorizons.angelica.utils.JsonUtil.loadStr;

public class JsonModel implements QuadProvider {

    @Nullable
    private final ResourceLocation parentId;
    @Nullable
    private JsonModel parent;
    private final boolean useAO;
    private final Map<ModelDisplay.Position, ModelDisplay> display;
    private final Map<String, String> textures;
    private List<ModelElement> elements;
    static final Gson GSON = new GsonBuilder().registerTypeAdapter(JsonModel.class, new Deserializer()).create();
    private List<Quad> bakedQuadStore = new ObjectArrayList<>();

    JsonModel(@Nullable ResourceLocation parentId, boolean useAO, Map<ModelDisplay.Position, ModelDisplay> display, Map<String, String> textures, List<ModelElement> elements) {
        this.parentId = parentId;
        this.useAO = useAO;
        this.display = display;
        this.textures = textures;
        this.elements = elements;
    }

    public void bake() {

        final NdQuadBuilder builder = new NdQuadBuilder();

        // Append faces from each element
        for (ModelElement e : this.elements) {

            final Vector3f from = e.getFrom();
            final Vector3f to = e.getTo();

            for (ModelElement.Face f : e.getFaces()) {

                // Assign vertexes
                for (int i = 0; i < 4; ++i) {

                    final Vector3f vert = NdQuadBuilder.mapSideToVertex(from, to, i, f.getName());
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
                    builder.uv(2, uv.z, uv.y);
                    builder.uv(3, uv.z, uv.w);
                } else {

                    // Not sure if this is correct, but it seems to fix things
                    flags |= NdQuadBuilder.BAKE_LOCK_UV;
                }

                // Set the sprite
                builder.spriteBake("", flags);

                // Set the tint index
                final int tint = f.getTintIndex();
                builder.color(tint, tint, tint, tint);

                // Bake and add it
                this.bakedQuadStore.add(builder.build(new Quad()));
            }
        }

        this.bakedQuadStore = new ObjectImmutableList<>(this.bakedQuadStore);
    }

    public List<ResourceLocation> getParents() {
        return Arrays.asList(parentId);
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {

        return this.bakedQuadStore.stream().map(q -> {
            final Quad q1 = quadPool.getInstance();
            q1.copyFrom(q);
            return q1;
        }).collect(ObjectImmutableList.toListWithExpectedSize(bakedQuadStore.size()));
    }

    public void resolveParents(Function<ResourceLocation, JsonModel> modelLoader) {

        JsonModel m = this;

        // If there should be a parent and there isn't, resolve it
        if (this.parentId != null && this.parent == null) {

            final JsonModel p = modelLoader.apply(this.parentId);
            p.resolveParents(modelLoader);

            // Inherit properties
            this.parent = p;
            if (this.elements.isEmpty()) this.elements = p.elements;
        }
    }

    private static class Deserializer implements JsonDeserializer<JsonModel> {

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
            final Map<ModelDisplay.Position, ModelDisplay> ret = new Object2ObjectArrayMap<>(ModelDisplay.Position.values().length);

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

            final Map<String, String> textures = new Object2ObjectArrayMap<>();

            if (in.has("textures")) {
                for (Map.Entry<String, JsonElement> e : in.getAsJsonObject("textures").entrySet()) {
                    textures.put(e.getKey(), e.getValue().getAsString());
                }
            }

            return textures;
        }

        private ModelElement.Rotation loadRotation(JsonObject in) {

            if (in.has("rotation")) {
                final JsonObject json = in.getAsJsonObject("rotation");

                final Vector3f origin = loadVec3(json, "origin");
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
                final String texture = loadStr(face, "texture");
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
