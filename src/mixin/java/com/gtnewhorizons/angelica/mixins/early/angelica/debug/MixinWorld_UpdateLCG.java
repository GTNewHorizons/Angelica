package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.mixins.interfaces.WorldRandomTickAccessor;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface MixinWorld_UpdateLCG extends WorldRandomTickAccessor {

    @Accessor("updateLCG")
    @Override
    void angelica$setUpdateLCG(int value);
}
