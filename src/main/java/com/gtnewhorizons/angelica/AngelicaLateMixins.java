package com.gtnewhorizons.angelica;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizons.angelica.mixins.Mixins;

import java.util.List;
import java.util.Set;

@LateMixin
@SuppressWarnings("unused")
public class AngelicaLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        int v = Runtime.version().feature();
        if (v >= 21) return "mixins.angelica.late.j21.json";
        if (v >= 17) return "mixins.angelica.late.j17.json";
        return "mixins.angelica.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return IMixins.getLateMixins(Mixins.class, loadedMods);
    }
}
