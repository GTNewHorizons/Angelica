package jss.notfine.util.itembreakparticles;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.item.ItemStack;

public interface IRenderGlobalSpawnItemBreakParticle {

    EntityFX spawnItemBreakParticle(ItemStack itemStack, final double x, final double y, final double z, double velocityX, double velocityY, double velocityZ);

}
