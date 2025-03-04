#if MC_VER == MC_1_16_5

package com.seibel.distanthorizons.forge.mixins.server;

import com.seibel.distanthorizons.common.wrappers.misc.IMixinServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity
{
	@Inject(at = @At("TAIL"), method = "setLevel")
	public void setLevel(Level level, CallbackInfo ci)
	{
		if (this instanceof IMixinServerPlayer)
		{
			((IMixinServerPlayer) this).distantHorizons$setDimensionChangeDestination((ServerLevel) level);
		}
	}
	
}

#else

package com.seibel.distanthorizons.forge.mixins.server;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class MixinEntity
{
}
#endif
