package com.embeddedt.chunkbert.mixin;

import com.embeddedt.chunkbert.ext.AnvilChunkLoaderExt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilChunkLoader.class)
public class AnvilChunkLoaderMixin implements AnvilChunkLoaderExt {
    private boolean chunkbert$loadTes = true;

    @Override
    public void chunkbert$setLoadsTileEntities(boolean b) {
        chunkbert$loadTes = b;
    }

    @Redirect(method = "loadEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NBTTagCompound;getTagList(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;"))
    private NBTTagList getTagList(NBTTagCompound compound, String key, int type) {
        if(!chunkbert$loadTes) {
            if(key.equals("TileEntities") || key.equals("TileTicks")) {
                return new NBTTagList();
            }
        }
        return compound.getTagList(key, type);
    }
}
