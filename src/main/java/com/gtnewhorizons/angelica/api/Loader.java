package com.gtnewhorizons.angelica.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.utils.Callback;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Loader {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(JsonModel.class, new JsonModel.Deserializer()).create();
    private static final List<ResourceLocation> unloadedModels = new ObjectArrayList<>();
    private static final Map<ResourceLocation, JsonModel> loadedModels = new Object2ObjectOpenHashMap<>();
    private static final Map<Variant, JsonModel> modelsToBake = new Object2ObjectOpenHashMap<>();
    private static final List<Callback> postBakeCallbacks = new ObjectArrayList<>();

    /**
     * Convenience method to register a baker callback, which will be run after JSON models are baked. Useful if
     * you implemented your own non-JSON model that still needs baking.
     */
    public static void registerBaker(Callback baker) {

        postBakeCallbacks.add(baker);
    }

    /**
     * Convenience method to register multiple variants. See {@link #registerModels(Callback, Collection)}.
     */
    public static void registerModels(Callback loader, Variant... variants) {

        registerModels(loader, Arrays.asList(variants));
    }

    /**
     * Pass the variant(s) you want to load, and if needed a callback which puts the models in a useful place, e.g.
     * saving them to a static field after baking.
     */
    public static void registerModels(Callback loader, Collection<Variant> variants) {

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

            final JsonModel model = loadJson(l, JsonModel.class);
            unloadedModels.addAll(model.getParents());
            loadedModels.put(l, model);
        }
    }

    private static <T> T loadJson(ResourceLocation path, Class<T> clazz) {
        try {

            final InputStream is = Minecraft.getMinecraft().getResourceManager().getResource(path).getInputStream();
            return GSON.fromJson(new InputStreamReader(is), clazz);
        } catch (IOException e) {

            AngelicaTweaker.LOGGER.fatal("Could not find " + path.getResourceDomain() + " " + path.getResourcePath());
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

    public static QuadProvider getModel(Variant loc) {
        return modelsToBake.get(loc);
    }
}
