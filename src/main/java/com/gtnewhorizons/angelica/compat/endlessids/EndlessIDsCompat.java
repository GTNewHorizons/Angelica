package com.gtnewhorizons.angelica.compat.endlessids;

import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import lombok.val;

import net.minecraft.world.chunk.Chunk;

import static com.falsepattern.endlessids.constants.ExtendedConstants.biomeIDMask;
import static com.falsepattern.endlessids.constants.ExtendedConstants.biomeIDNull;

public class EndlessIDsCompat {
    public static void sodium$populateBiomes(Chunk chunk) {
        if (!(chunk instanceof ChunkBiomeHook hook))
            return;

        val biomes = hook.getBiomeShortArray();
        if (biomes == null)
            return;

        val cX = chunk.xPosition << 4;
        val cZ = chunk.zPosition << 4;

        val manager = chunk.worldObj.getWorldChunkManager();
        for(var z = 0; z < 16; z++) {
            for(var x = 0; x < 16; x++) {
                val idx = (z << 4) + x;
                val biome = biomes[idx] & biomeIDMask;
                if(biome == biomeIDNull) {
                    val generated = manager.getBiomeGenAt(cX + x, cZ + z);
                    if (generated == null)
                        continue;
                    biomes[idx] = (short)(generated.biomeID & biomeIDMask);
                }
            }
        }
    }
}
