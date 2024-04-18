package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer;

import com.prupe.mcpatcher.cit.CITUtils;
import jss.notfine.util.itembreakparticles.IRenderGlobalSpawnItemBreakParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RenderGlobal.class)
public abstract class MixinRenderGlobal implements IRenderGlobalSpawnItemBreakParticle {

    public EntityFX spawnItemBreakParticle(ItemStack itemStack, final double x, final double y, final double z, double velocityX, double velocityY, double velocityZ) {
        int i = mc.gameSettings.particleSetting;

        if (i == 1 && theWorld.rand.nextInt(3) == 0)  {
            i = 2;
        }
        if (i > 1) {
            return null;
        }

        double relX = this.mc.renderViewEntity.posX - x;
        double relY = this.mc.renderViewEntity.posY - y;
        double relZ = this.mc.renderViewEntity.posZ - z;

        double distance = 16.0D;

        if (relX * relX + relY * relY + relZ * relZ > distance * distance) {
            return null;
        }

        Item item = itemStack.getItem();
        int meta = itemStack.getItemDamage();
        EntityFX entityfx = new EntityBreakingFX(theWorld, x, y, z, velocityX, velocityY, velocityZ, item, meta);
        entityfx.particleIcon = CITUtils.getIcon(item.getIconFromDamage(meta), itemStack, 0);
        mc.effectRenderer.addEffect(entityfx);
        return entityfx;
    }

    @Shadow
    private Minecraft mc;
    @Shadow
    private WorldClient theWorld;

}
