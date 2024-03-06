package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends EntityLivingBase implements IDynamicLightSource {

    public MixinEntityPlayerSP(World p_i1595_1_) {
        super(p_i1595_1_);
    }

    @Unique
    protected int angelica$luminance;

    @Override
    public void angelica$dynamicLightTick() {
//        if (!DynamicLightHandlers.canLightUp(this)) {
//            this.angelica$luminance = 0;
//            return;
//        }

        if (this.fire > 0) {
            this.angelica$luminance = 15;
        } else {
            int luminance = 15; //DynamicLightHandlers.getLuminanceFrom((Entity) this);

            boolean submergedInFluid = this.isInWater();

            // todo check holding item for luminance
//            for (var equipped : this.getAllSlots()) {
//                if (!equipped.isEmpty())
//                    luminance = Math.max(luminance, LambDynLights.getLuminanceFromItemStack(equipped, submergedInFluid));
//            }

            this.angelica$luminance = luminance;
        }
    }

    @Override
    public int angelica$getLuminance() {
        return this.angelica$luminance;
    }
}
