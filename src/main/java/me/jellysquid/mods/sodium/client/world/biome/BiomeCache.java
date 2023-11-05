package me.jellysquid.mods.sodium.client.world.biome;

import com.gtnewhorizons.angelica.compat.mojang.Biome;
import com.gtnewhorizons.angelica.compat.mojang.BiomeAccess;
import com.gtnewhorizons.angelica.compat.mojang.BiomeAccessType;
import me.jellysquid.mods.sodium.client.world.ClientWorldExtended;
import net.minecraft.world.World;

import java.util.Arrays;

public class BiomeCache {
    private final BiomeAccessType type;
    private final long seed;

    private final Biome[] biomes;

    public BiomeCache(World world) {
        this.type = world.getDimension().getBiomeAccessType();
        this.seed = ((ClientWorldExtended) world).getBiomeSeed();

        this.biomes = new Biome[16 * 16];
    }

    public Biome getBiome(BiomeAccess.Storage storage, int x, int y, int z) {
        int idx = ((z & 15) << 4) | (x & 15);

        Biome biome = this.biomes[idx];

        if (biome == null) {
            this.biomes[idx] = biome = this.type.getBiome(this.seed, x, y, z, storage);
        }

        return biome;
    }

    public void reset() {
        Arrays.fill(this.biomes, null);
    }
}
