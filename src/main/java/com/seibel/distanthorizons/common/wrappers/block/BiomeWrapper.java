package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.world.biome.BiomeGenBase;

import java.io.IOException;

public class BiomeWrapper implements IBiomeWrapper {
    public static final String PLAINS_RESOURCE_LOCATION_STRING = "";

    public static IBiomeWrapper deserialize(String plainsResourceLocationString, ILevelWrapper levelWrapper) throws IOException {
        return null;
    }

    public static IDhApiBiomeWrapper getBiomeWrapper(BiomeGenBase biome, ILevelWrapper coreLevelWrapper) {
        return null;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getSerialString() {
        return "";
    }

    @Override
    public Object getWrappedMcObject() {
        return null;
    }
}
