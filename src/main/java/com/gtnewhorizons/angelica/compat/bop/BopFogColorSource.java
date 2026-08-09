package com.gtnewhorizons.angelica.compat.bop;

import biomesoplenty.client.fog.IBiomeFog;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class BopFogColorSource implements BopFogBlend.ColorSource {

    public static final BopFogColorSource INSTANCE = new BopFogColorSource();

    private World world;

    private BopFogColorSource() {}

    public BopFogColorSource forWorld(World world) {
        this.world = world;
        return this;
    }

    @Override
    public int fogColour(int x, int y, int z) {
        final BiomeGenBase biome = FogBiomeCache.get(world, x, z);
        if (!(biome instanceof IBiomeFog fog)) return BopFogBlend.NO_FOG;
        return fog.getFogColour(x, y, z) & 0xFFFFFF;
    }
}
