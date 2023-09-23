package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving extends EntityLivingBase {
    @Shadow public static Item getArmorItemForSlot(int p_82161_0_, int p_82161_1_) {
        throw new AssertionError();
    }

    @Shadow public abstract ItemStack func_130225_q(int p_130225_1_);

    @Shadow protected float[] equipmentDropChances;

    public MixinEntityLiving(World p_i1594_1_) {
        super(p_i1594_1_);
    }

    @ModifyConstant(method = "despawnEntity", constant = @Constant(doubleValue = 16384.0D))
    private double lowerHardRange(double old) {
        if(!ArchaicConfig.fixMobSpawnsAtLowRenderDist)
            return old;
        if(worldObj.isRemote)
            return old;
        if(((WorldServer)worldObj).func_73046_m().getConfigurationManager().getViewDistance() < 10)
            return 96 * 96;
        return old;
    }

    /**
     * @reason Higher armor chances (from TMCW).
     * @author embeddedt
     */
    @Inject(method = "addRandomArmor", at = @At("HEAD"), cancellable = true)
    public void addRandomArmor(CallbackInfo ci)
    {
        if(!ArchaicConfig.increaseMobArmor)
            return;
        float var1 = this.worldObj.func_147462_b(this.posX, this.posY, this.posZ);

        if (this.rand.nextFloat() < var1 * 0.2F)
        {
            float var2 = 0.4F / (var1 * 2.0F + 1.0F);
            int var3 = this.rand.nextInt(2);

            if (this.rand.nextFloat() < 0.2F)
            {
                ++var3;
            }

            if (this.rand.nextFloat() < 0.2F)
            {
                ++var3;
            }

            if (this.rand.nextFloat() < 0.2F)
            {
                ++var3;
            }

            for (int var4 = 3; var4 >= 0; --var4)
            {
                ItemStack var5 = this.func_130225_q(var4);

                if (var4 < 3 && this.rand.nextFloat() < var2)
                {
                    break;
                }

                if (var5 == null)
                {
                    Item var6 = getArmorItemForSlot(var4 + 1, var3);

                    if (var6 != null)
                    {
                        this.setCurrentItemOrArmor(var4 + 1, new ItemStack(var6));

                        if (var3 == 5)
                        {
                            this.equipmentDropChances[var4 + 1] = 0.1F;
                        }
                    }
                }
            }
        }
        ci.cancel();
    }
}
