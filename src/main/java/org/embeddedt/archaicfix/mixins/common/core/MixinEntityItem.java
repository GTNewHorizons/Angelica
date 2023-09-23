package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityItem.class)
public abstract class MixinEntityItem extends Entity {
    @Shadow public int age;

    private double arch$oldMotionX, arch$oldMotionY, arch$oldMotionZ;

    private boolean arch$movedThisTick;

    public MixinEntityItem(World p_i1582_1_) {
        super(p_i1582_1_);
    }

    private boolean arch$shouldItemMove() {
        if(!this.onGround)
            return true;
        double horzMotion = this.motionX * this.motionX + this.motionZ * this.motionZ;
        if(horzMotion > 1.0e-5f)
            return true;
        return (this.age + this.getEntityId()) % 4 == 0;
    }

    @Redirect(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/item/EntityItem;moveEntity(DDD)V", ordinal = 0))
    private void moveIfNecessary(EntityItem item, double x, double y, double z) {
        arch$movedThisTick = arch$shouldItemMove();
        if(arch$movedThisTick)
            item.moveEntity(x, y, z);
        else {
            arch$oldMotionX = item.motionX;
            arch$oldMotionY = item.motionY;
            arch$oldMotionZ = item.motionZ;
        }
    }

    @Inject(method = "onUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/item/EntityItem;age:I", opcode = Opcodes.PUTFIELD))
    private void restoreVelocityIfNotMoving(CallbackInfo ci) {
        if(!arch$movedThisTick) {
            this.motionX = arch$oldMotionX;
            this.motionY = arch$oldMotionY;
            this.motionZ = arch$oldMotionZ;
        }
    }
}
