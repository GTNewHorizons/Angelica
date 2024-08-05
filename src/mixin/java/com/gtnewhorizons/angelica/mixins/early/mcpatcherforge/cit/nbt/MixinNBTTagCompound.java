package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.nbt;

import java.util.Collection;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import jss.notfine.util.NBTTagCompoundExpansion;

@Mixin(NBTTagCompound.class)
public abstract class MixinNBTTagCompound implements NBTTagCompoundExpansion {

    @Shadow
    private Map<String, NBTBase> tagMap;

    public Collection<NBTBase> getTags() {
        return this.tagMap.values();
    }
}
