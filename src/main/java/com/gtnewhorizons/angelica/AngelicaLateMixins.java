package com.gtnewhorizons.angelica;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizons.angelica.mixins.Mixins;

import java.util.List;
import java.util.Set;

@LateMixin
public class AngelicaLateMixins implements ILateMixinLoader {
    @Override
    public String getMixinConfig() {
        return "mixins.angelica.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Mixins.getLateMixins(loadedMods);
    }

}
