package com.gtnewhorizons.angelica.models.json;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

import static com.gtnewhorizons.angelica.models.json.JsonModel.GSON;

/**
 * Model loading should proceed as follows:
 * Models to load are registered in PREINIT.
 * All registered models are recursively loaded in INIT.
 * Models registered for baking are baked on the first tick of the game.
 *
 * As for icons, register them Block::registerBlockIcons(). Whatever gets them in the block texture atlas.
 */
public class Loader {

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

            try (final InputStream is = Loader.class.getResourceAsStream(
                "/assets/" + l.getResourceDomain() + "/models/" + l.getResourcePath() + ".json"
            )) {

                final JsonModel model = GSON.fromJson(new InputStreamReader(is), JsonModel.class);
                unloadedModels.addAll(model.getParents());
                loadedModels.put(l, model);
            } catch (IOException | NullPointerException e) {

                AngelicaTweaker.LOGGER.fatal("Could not find /assets/" + l.getResourceDomain() + "/models/" + l.getResourcePath() + ".json");
                throw new RuntimeException(e);
            }
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
