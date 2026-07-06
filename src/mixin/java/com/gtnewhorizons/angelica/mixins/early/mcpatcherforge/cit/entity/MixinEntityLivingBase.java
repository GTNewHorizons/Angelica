package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.entity;

import jss.notfine.NotFine;
import jss.notfine.util.itembreakparticles.IWorldSpawnItemBreakParticle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    /**
     * @author jss2a98aj
     * @reason Fix item break particles
     */
    @Overwrite
    public void renderBrokenItemStack(ItemStack itemStack) {
        playSound("random.break", 0.8F, 0.8F + this.worldObj.rand.nextFloat() * 0.4F);

        for(int i = 0; i < 5; ++i) {
            Vec3 vec3 = Vec3.createVectorHelper(((double)rand.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3.rotateAroundX(-rotationPitch * 3.1415927F / 180.0F);
            vec3.rotateAroundY(-rotationYaw * 3.1415927F / 180.0F);
            Vec3 vec31 = Vec3.createVectorHelper(((double)rand.nextFloat() - 0.5) * 0.3, (double)(-rand.nextFloat()) * 0.6 - 0.3, 0.6);
            vec31.rotateAroundX(-rotationPitch * 3.1415927F / 180.0F);
            vec31.rotateAroundY(-rotationYaw * 3.1415927F / 180.0F);
            vec31 = vec31.addVector(posX, posY + (double)getEyeHeight(), posZ);
            ((IWorldSpawnItemBreakParticle)worldObj).spawnItemBreakParticle(itemStack,
                "iconcrack_" + Item.getIdFromItem(itemStack.getItem()),
                vec31.xCoord, vec31.yCoord, vec31.zCoord, vec3.xCoord, vec3.yCoord + 0.05, vec3.zCoord);
        }

    }

    private MixinEntityLivingBase(World world) { super(world); }

}
