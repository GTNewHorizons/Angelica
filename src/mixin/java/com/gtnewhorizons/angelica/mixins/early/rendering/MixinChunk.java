package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.endlessids.EndlessIDsCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public abstract class MixinChunk {
    @Shadow
    public World worldObj;

    @Shadow
    private byte[] blockBiomeArray;

    @Shadow
    @Final
    public int xPosition, zPosition;

    @Inject(method = "fillChunk", at = @At("RETURN"))
    private void sodium$populateBiomes(CallbackInfo ci) {
        if(this.worldObj.isRemote && !Minecraft.getMinecraft().isSingleplayer()) {
            // We are in multiplayer, the server might not have sent all biomes to the client.
            // Populate them now while we're on the main thread.
            if (ModStatus.isEIDBiomeLoaded) {
                EndlessIDsCompat.sodium$populateBiomes((Chunk) (Object) this);
            } else {
                WorldChunkManager manager = this.worldObj.getWorldChunkManager();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int idx = (z << 4) + x;
                        int biome = this.blockBiomeArray[idx] & 255;
                        if (biome == 255) {
                            BiomeGenBase generated = manager.getBiomeGenAt((this.xPosition << 4) + x, (this.zPosition << 4) + z);
                            this.blockBiomeArray[idx] = (byte) (generated.biomeID & 255);
                        }
                    }
                }
            }
        }
    }
}
