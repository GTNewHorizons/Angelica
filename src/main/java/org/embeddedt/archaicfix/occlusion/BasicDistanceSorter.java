package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.embeddedt.archaicfix.helpers.WorldRendererDistanceHelper;

import java.util.Comparator;

public class BasicDistanceSorter implements Comparator<WorldRenderer> {
    private final EntityLivingBase renderViewEntity;

    public BasicDistanceSorter(EntityLivingBase renderViewEntity) {
        this.renderViewEntity = renderViewEntity;
    }

    @Override
    public int compare(WorldRenderer wr1, WorldRenderer wr2) {
        return (int)((WorldRendererDistanceHelper.betterDistanceSquared(renderViewEntity, wr1) - WorldRendererDistanceHelper.betterDistanceSquared(renderViewEntity, wr2)) * 1024D);
    }
}
