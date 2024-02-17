package com.gtnewhorizons.angelica.models.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

/**
 * Model loading should proceed as follows: <ul>
 * <li>Models to load are registered in PREINIT.</li>
 * <li>All registered models are recursively loaded in INIT.</li>
 * <li>Models registered for baking are baked on the first tick of the game.</li>
 *</ul>
 * <p>Alternatively, you can register blocks with blockstate variants in preinit, and the rest is handled by
 * {@link Loader}.
 * <p>As for icons, register them in {@link Block#registerBlockIcons}. Whatever gets them in the block texture atlas.
 */
public class Loader {

    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(JsonModel.class, new JsonModel.Deserializer()).create();
    private static final List<ResourceLocation> unloadedModels = new ObjectArrayList<>();
    private static final Map<ResourceLocation, JsonModel> loadedModels = new Object2ObjectOpenHashMap<>();
    private static final List<ResourceLocation> modelsToBake = new ObjectArrayList<>();

    public static void registerModel(ResourceLocation loc) {
        unloadedModels.add(loc);
        modelsToBake.add(loc);
    }

    public static void loadModels() {

        for (ResourceLocation l : unloadedModels) {

            if (l == null) continue;
            if (loadedModels.containsKey(l)) continue;

            final JsonModel model = loadJson(
                "/assets/" + l.getResourceDomain() + "/models/" + l.getResourcePath() + ".json", JsonModel.class);
            unloadedModels.addAll(model.getParents());
            loadedModels.put(l, model);
        }
    }

    private static <T> T loadJson(String path, Class<T> clazz) {
        try (final InputStream is = Loader.class.getResourceAsStream(path)) {

            return GSON.fromJson(new InputStreamReader(is), clazz);

        } catch (IOException | NullPointerException e) {

            AngelicaTweaker.LOGGER.fatal("Could not find " + path);
            throw new RuntimeException(e);
        }
    }

    public static void bakeModels() {

        for (ResourceLocation l : modelsToBake) {

            final JsonModel dough = loadedModels.get(l);

            // Resolve the parent chain
            dough.resolveParents(loadedModels::get);

            // Bake
            dough.bake();
        }
    }

    public static JsonModel getModel(ResourceLocation loc) {
        return loadedModels.get(loc);
    }
}
