package jss.notfine.util.itembreakparticles;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public interface IWorldSpawnItemBreakParticle {

    void spawnItemBreakParticle(ItemStack itemStack, String particleName, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

}
