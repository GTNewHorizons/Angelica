package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.mixins.TargetedMod;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.coreMods;

@LateMixin
public class AngelicaLateMixins implements ILateMixinLoader {

    public static final String TWILIGHT_FOREST = "TwilightForest";
    public static final String THAUMCRAFT = "Thaumcraft";
    public static final String WITCHERY = "witchery";

    @Override
    public String getMixinConfig() {
        return "mixins.angelica.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        final List<String> mixins = new ArrayList<>();
        final List<String> notLoading = new ArrayList<>();
        for (Mixins mixin : Mixins.values()) {
            if (mixin.phase == Mixins.Phase.LATE) {
                if (mixin.shouldLoad(Collections.emptySet(), loadedMods)) {
                    mixins.addAll(mixin.mixinClasses);
                } else {
                    notLoading.addAll(mixin.mixinClasses);
                }
            }
        }
        AngelicaTweaker.LOGGER.info("Not loading the following LATE mixins: {}", notLoading.toString());
        // TODO: Sodium
//        mixins.addAll(getNotFineMixins(loadedMods));
//        mixins.addAll(getArchaicMixins(loadedMods));
        return mixins;
    }

    private List<String> getNotFineMixins(Set<String> loadedMods) {
        if(FMLLaunchHandler.side().isServer())
            return Collections.emptyList();

        final List<String> mixins = new ArrayList<>();

        if(loadedMods.contains(THAUMCRAFT)) {
            mixins.add("leaves.thaumcraft.MixinBlockMagicalLeaves");
        }

        if(loadedMods.contains(TWILIGHT_FOREST)) {
            mixins.add("leaves.twilightforest.MixinBlockTFLeaves");
            mixins.add("leaves.twilightforest.MixinBlockTFLeaves3");

            //Non-GTNH Twilight Forest builds will break horribly with this mixin.
            boolean modernBuild = false;
            try {
                ArtifactVersion accepted = new DefaultArtifactVersion(TWILIGHT_FOREST, VersionRange.createFromVersionSpec("[2.3.8.18,)"));
                ModContainer mc = Loader.instance().getIndexedModList().get(TWILIGHT_FOREST);
                if(mc != null) modernBuild = accepted.containsVersion(mc.getProcessedVersion());
            } catch (InvalidVersionSpecificationException ignored) {}

            if(modernBuild) {
                mixins.add("leaves.twilightforest.MixinBlockTFMagicLeaves");
            }
        }

        if(loadedMods.contains(WITCHERY)) {
            mixins.add("leaves.witchery.MixinBlockWitchLeaves");
        }
        return mixins;
    }

    public List<String> getArchaicMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        Set<TargetedMod> validMods = new HashSet<>(coreMods);
        HashMap<String, TargetedMod> modById = new HashMap<>();
        for(TargetedMod t : TargetedMod.values()) {
            if(t.getModId() != null) modById.put(t.getModId(), t);
        }
        for(String modId : loadedMods) {
            TargetedMod t = modById.get(modId);
            if(t != null) validMods.add(t);
        }
        return mixins;
    }
}
