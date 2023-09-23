package org.embeddedt.archaicfix;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;
import java.util.List;

public final class LeftClickEventHandler {

    public LeftClickEventHandler() {}

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent event) {
        if(event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            Block block = event.world.getBlock(event.x, event.y, event.z);
            if(block.getCollisionBoundingBoxFromPool(event.world, event.x, event.y, event.z) != null) {
                return;
            }

            EntityPlayer entityPlayer = event.entityPlayer;

            float blockReachDistance = 4.5F;

            Vec3 from = Vec3.createVectorHelper(entityPlayer.posX, entityPlayer.posY + (double)entityPlayer.getEyeHeight(), entityPlayer.posZ);
            Vec3 vec3d = entityPlayer.getLook(1.0F);
            Vec3 to = from.addVector(vec3d.xCoord * blockReachDistance, vec3d.yCoord * blockReachDistance, vec3d.zCoord * blockReachDistance);

            Entity targetEntity = getEntityClosestToStartPos(entityPlayer, event.world, from, to);

            if(targetEntity != null) {
                if (!event.world.isRemote) {
                    entityPlayer.attackTargetEntityWithCurrentItem(targetEntity);
                }
                event.setCanceled(true);
            }
        }


    }

    private AxisAlignedBB safeGenBoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return AxisAlignedBB.getBoundingBox(
                Math.min(minX, maxX),
                Math.min(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minX, maxX),
                Math.max(minY, maxY),
                Math.max(minZ, maxZ)
        );
    }

    private Entity getEntityClosestToStartPos(Entity entityIn, World world, Vec3 startPos, Vec3 endPos) {
        Entity entity = null;
        AxisAlignedBB searchBB = safeGenBoundingBox(startPos.xCoord,startPos.yCoord,startPos.zCoord, endPos.xCoord, endPos.yCoord, endPos.zCoord);
        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(entityIn, searchBB, new IEntitySelector() {
            @Override
            public boolean isEntityApplicable(Entity p_82704_1_) {
                return p_82704_1_ != null && p_82704_1_.canBeCollidedWith();
            }
        });

        double d0 = 0.0D;
        AxisAlignedBB axisAlignedBB;

        for(Entity entity1 : list) {
            axisAlignedBB = entity1.boundingBox.expand(0.3D, 0.3D, 0.3D);
            MovingObjectPosition raytraceResult = axisAlignedBB.calculateIntercept(startPos, endPos);

            if(raytraceResult != null) {
                double d1 = startPos.squareDistanceTo(raytraceResult.hitVec);

                if(d1 < d0 || d0 == 0.0D) {
                    entity = entity1;
                    d0 = d1;
                }
            }
        }
        return entity;
    }
}