package com.gtnewhorizons.angelica.models.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.utils.Callback;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
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
    private static final Map<Variant, JsonModel> modelsToBake = new Object2ObjectOpenHashMap<>();
    private static final List<Callback> postBakeCallbacks = new ObjectArrayList<>();

    /**
     * Pass the variant you want to load, and if needed a callback which puts the models in a useful place, e.g. saving
     * them to a static field after baking.
     */
    public static void registerModel(Variant loc, Callback loader) {
        unloadedModels.add(loc.getModel());
        modelsToBake.put(loc, null);
        postBakeCallbacks.add(loader);
    }

    /**
     * Convenience method to register multiple variants. See {@link #registerModel}.
     */
    public static void registerModels(Callback loader, Variant... variants) {

        registerModels(Arrays.asList(variants), loader);
    }

    /**
     * Convenience method to register multiple variants. See {@link #registerModel}.
     */
    public static void registerModels(Collection<Variant> variants, Callback loader) {

        for (Variant v : variants) {

            unloadedModels.add(v.getModel());
            modelsToBake.put(v, null);
        }

        postBakeCallbacks.add(loader);
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

        for (Map.Entry<Variant, JsonModel> l : modelsToBake.entrySet()) {

            final JsonModel dough = new JsonModel(loadedModels.get(l.getKey().getModel()));

            // Resolve the parent chain
            dough.resolveParents(loadedModels::get);

            // Bake
            dough.bake(l.getKey());

            l.setValue(dough);
        }

        for (Callback c : postBakeCallbacks)
            c.run();
    }

    public static JsonModel getModel(Variant loc) {
        return modelsToBake.get(loc);
    }
}
