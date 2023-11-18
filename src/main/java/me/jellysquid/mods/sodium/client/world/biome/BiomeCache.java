package me.jellysquid.mods.sodium.client.world.biome;

import com.gtnewhorizons.angelica.compat.mojang.BiomeAccess;
import com.gtnewhorizons.angelica.compat.mojang.BiomeAccessType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Arrays;

public class BiomeCache {
    private final BiomeAccessType type;
    private final long seed;

    private final BiomeGenBase[] biomes;

    public BiomeCache(World world) {
        // TODO: Sodium
        this.type = null;//world.getDimension().getBiomeAccessType(); // Provider?
        this.seed = world.getSeed();

        this.biomes = new BiomeGenBase[16 * 16];
    }

    public BiomeGenBase getBiome(BiomeAccess.Storage storage, int x, int y, int z) {
        int idx = ((z & 15) << 4) | (x & 15);

        BiomeGenBase biome = this.biomes[idx];

        if (biome == null && this.type != null) {
            this.biomes[idx] = biome = this.type.getBiome(this.seed, x, y, z, storage);
        }

        return biome;
    }

    public void reset() {
        Arrays.fill(this.biomes, null);
    }
}
